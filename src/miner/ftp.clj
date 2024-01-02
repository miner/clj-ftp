;; Apache Commons Net API:
;; https://commons.apache.org/proper/commons-net/apidocs/index.html

;; Uses Apache Commons Net 3.9.  Does not support SFTP, but does support FTPS.

;; FTP is considered insecure.  Data and passwords are sent in the
;; clear so someone could sniff packets on your network and discover
;; your password.  Nevertheless, FTP access is useful for dealing with anonymous
;; FTP servers and situations where security is not an issue.

(ns miner.ftp
  (:import [org.apache.commons.net.ftp FTP FTPClient FTPSClient FTPFile FTPReply]
           [java.net URI URL]
           [java.io File IOException FileOutputStream OutputStream FileInputStream InputStream])
  (:require [me.raynes.fs :as fs]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn as-uri ^URI [url]
  (cond (instance? URL url) (.toURI ^URL url)
        (instance? URI url) url
        :else               (URI. url)))

(defn open
  ([url] (open url "UTF-8" {}))
  ([url control-encoding] (open url control-encoding {}))
  ([url control-encoding
    {:keys [strict-reply-parsing
            security-mode
            data-timeout-ms
            connect-timeout-ms
            default-timeout-ms
            control-keep-alive-timeout-sec
            control-keep-alive-reply-timeout-ms]
     :or {strict-reply-parsing true
          security-mode :explicit
          data-timeout-ms -1
          connect-timeout-ms 30000
          control-keep-alive-timeout-sec 300
          control-keep-alive-reply-timeout-ms 1000}}]
   (let [implicit? (not= :explicit security-mode)
         ^URI uri (as-uri url)
         ^FTPClient client (case (.getScheme uri)
                             "ftp" (FTPClient.)
                             "ftps" (FTPSClient. implicit?)
                             (throw (Exception. (str "unexpected protocol " (.getScheme uri) " in FTP url, need \"ftp\" or \"ftps\""))))]
     ;; (.setAutodetectUTF8 client true)
     (when default-timeout-ms (.setDefaultTimeout client default-timeout-ms))
     (.setStrictReplyParsing client strict-reply-parsing)
     (.setControlEncoding client control-encoding)
     (.setConnectTimeout client connect-timeout-ms)
     (.setDataTimeout client ^int data-timeout-ms)
     (.setControlKeepAliveTimeout client ^int control-keep-alive-timeout-sec)
     (.setControlKeepAliveReplyTimeout client ^int control-keep-alive-reply-timeout-ms)
     (.connect client
               (.getHost uri)
               (if (= -1 (.getPort uri)) (int 21) (.getPort uri)))
     (let [reply (.getReplyCode client)]
       (when-not (FTPReply/isPositiveCompletion reply)
         (.disconnect client)
         (throw (ex-info "Connection failed" {:reply-code   reply
                                              :reply-string (.getReplyString client)}))))
     client)))

(defn guess-file-type [file-name]
  "Best guess about the file type to use when transferring a given file based on the extension.
  Returns either :binary or :ascii (the default).  If you don't know what you're dealing with,
  this might help, but don't bet the server farm on it.  See also `client-set-file-type`."
  (case (str/lower-case (fs/extension file-name))
    (".jpg" ".jpeg" ".zip" ".mov" ".bin" ".exe" ".pdf" ".gz" ".tar" ".dmg" ".jar" ".tgz" ".war"
     ".lz" ".mp3" ".mp4" ".sit" ".z" ".dat" ".o" ".app" ".png" ".gif" ".class" ".avi" ".m4v"
     ".mpg" ".mpeg" ".swf" ".wmv" ".ogg") :binary
     :ascii))

(defn client-set-file-type [^FTPClient client filetype]
  "Set the file type for transfers to either :binary or :ascii (the default)"
  (if (= filetype :binary)
    (.setFileType client FTP/BINARY_FILE_TYPE)
    (.setFileType client FTP/ASCII_FILE_TYPE))
  filetype)

(defn user-info
  "Decode the user info part of a URL to extract the username and password.

  Note that URI#getUserInfo() isn't used because if the result of that method
  contains more than one ':' character, we can't determine which ':' is the
  separator.  At the same time, we can't easily use URLDecoder, because it
  converts '+' into spaces.  So we have to do the percent-decoding ourselves."
  [url control-encoding]
  (letfn [(decode [s]
            (when s
              (str/replace s
                           #"(%[0-9a-fA-F]{2})+"
                           (fn [[match & _]]
                             (String. ^bytes (->> (.split (subs match 1) "%")
                                                  (map #(.byteValue (Integer/decode (str "0x" %))))
                                                  (byte-array))
                                      ^String control-encoding)))))]
    (when-let [[encoded-uname encoded-pass] (when-let [ui (.getRawUserInfo (as-uri url))]
                                              (.split ui ":" 2))]
      [(decode encoded-uname) (decode encoded-pass)])))

(defn login* [^FTPClient client url username password]
  (when-not (.login client username password)
    (throw (ex-info (format "Unable to login with username: \"%s\"." username)
                    {:url          url
                     :invalid-user username}))))

(defmacro with-ftp
  "Establish an FTP connection, bound to client, for the FTP url, and execute the body with
   access to that client connection.  Closes connection at end of body.  Keyword
   options can follow the url in the binding vector.  By default, uses a passive local data
   connection mode and  ASCII file type.
   Use [client url :local-data-connection-mode :active
                   :file-type :binary
                   :security-mode :explicit] to override.

   Allows to override the following timeouts:
     - `connect-timeout-ms` - The timeout used when opening a socket. Default 30000
     - `data-timeout-ms` - the underlying socket timeout. Default - infinite (< 1).
     - `control-keep-alive-timeout-sec` - control channel keep alive message
       timeout. Default 300 seconds.
     - `control-keep-alive-reply-timeout-ms` - how long to wait for the control
       channel keep alive replies. Default 1000 ms.
     - `control-encoding` - The new character encoding for the control connection. Default - UTF-8
     - `username` - FTP username (if not supplying credentials via the URL)
     - `password` - FTP password (if not supplying credentials via the URL)"
  [[client url & {:keys [local-data-connection-mode file-type
                         control-encoding
                         username password]
                  :as params
                  :or {control-encoding "UTF-8"}}] & body]
  `(let [local-mode# ~local-data-connection-mode
         u# (as-uri ~url)
         ~client ^FTPClient (open u# ~control-encoding ~params)
         file-type# ~file-type]
     (try
       (if-let [[uname# pass#] (user-info u# ~control-encoding)]
         (login* ~client u# uname# pass#)                    ;; URL embedded credentials
         (login* ~client u# ~username ~password))            ;; Explicit credentials via params
       (let [path# (.getPath u#)]
         (when-not (or (str/blank? path#) (= path# "/"))
           (when-not (.changeWorkingDirectory ~client (subs path# 1))
             (throw (ex-info (format "Unable to change working directory to \"%s\"."
                                     path#)
                             {:url u#
                              :invalid-path path#})))))
       (client-set-file-type ~client file-type#)
       ;; by default (when nil) use passive mode
       (if (= local-mode# :active)
         (.enterLocalActiveMode ~client)
         (.enterLocalPassiveMode ~client))
       ~@body
       (finally (when (.isConnected ~client)
                  (try
                    (.disconnect ~client)
                    (catch IOException e# nil)))))))


(defn client-FTPFiles-all [^FTPClient client]
  (vec (.listFiles client)))

(defn client-FTPFiles [^FTPClient client]
  (filterv (fn [f] (and f (.isFile ^FTPFile f))) (.listFiles client)))

(defn client-FTPFile-directories [^FTPClient client]
  (vec (.listDirectories client)))

(defn client-all-names [^FTPClient client]
  (vec (.listNames client)))

(defn client-file-names [^FTPClient client]
  (mapv #(.getName ^FTPFile %) (client-FTPFiles client)))

(defn client-directory-names [^FTPClient client]
  (mapv #(.getName ^FTPFile %) (client-FTPFile-directories client)))

(defn client-complete-pending-command
  "Complete the previous command and check the reply code. Throw an exception if
   reply code is not a positive completion"
   [^FTPClient client]
  (.completePendingCommand client)
  (let [reply-code (.getReplyCode client)]
     (when-not (FTPReply/isPositiveCompletion reply-code)
       (throw (ex-info "Not a Positive completion of last command" {:reply-code reply-code
                                                                    :reply-string (.getReplyString client)})))))

(defn client-get
  "Get a file and write to local file-system (must be within a with-ftp)"
  ([client fname] (client-get client fname (fs/base-name fname)))

  ([client fname local-name]
      (with-open [outstream (FileOutputStream. (io/as-file local-name))]
        (.retrieveFile ^FTPClient client ^String fname ^OutputStream outstream))))

(defn client-get-stream
  "Get a file and return InputStream (must be within a with-ftp). Note that it's necessary to complete
   this command with a call to `client-complete-pending-command` after using the stream."
  ^InputStream [client fname]
  (.retrieveFileStream ^FTPClient client ^String fname))

(defn client-put
  "Put a file (must be within a with-ftp)"
  ([client fname] (client-put client fname (fs/base-name fname)))

  ([client fname remote] (with-open [instream (FileInputStream. (io/as-file fname))]
                           (.storeFile ^FTPClient client ^String remote ^InputStream instream))))

(defn client-put-stream
  "Put an InputStream (must be within a with-ftp)"
  [client instream remote]
  (.storeFile ^FTPClient client ^String remote ^InputStream instream))

(defn client-cd [client dir]
  (.changeWorkingDirectory ^FTPClient client ^String dir))

(defn- strip-double-quotes [^String s]
  (let [len (count s)]
    (cond (<= len 2) s
          (and (= (.charAt s 0) \")
               (= (.charAt s (dec len)) \")) (subs s 1 (dec len))
          :else s)))

(defn client-pwd [client]
  (strip-double-quotes (.printWorkingDirectory ^FTPClient client)))

(defn client-mkdir [client subdir]
  (.makeDirectory ^FTPClient client ^String subdir))

;; Regular mkdir can only make one level at a time; mkdirs makes nested paths in the correct order
(defn client-mkdirs [client subpath]
  (doseq [d (reductions (fn [path item] (str path File/separator item))  (fs/split subpath))]
    (client-mkdir client d)))

(defn client-delete [client fname]
  "Delete a file (must be within a with-ftp)"
  (.deleteFile ^FTPClient client ^String fname))

(defn client-rename [client from to]
  "Rename a remote file (must be within a with-ftp"
  (.rename ^FTPClient client ^String from ^String to))


(defn client-send-site-command [client sitecmd ]
   "Send Site Command must be within with-ftp"
   (.sendSiteCommand ^FTPClient client ^String  sitecmd))

;; convenience methods for one-shot results

(defn rename-file [url from to]
  (with-ftp [client url]
    (client-rename client from to)))

(defn retrieve-file
  ([url fname] (retrieve-file url fname (fs/base-name fname)))
  ([url fname local-file]
     (with-ftp [client url]
       (client-get client fname (io/as-file local-file)))))

(defn list-all [url]
  (with-ftp [client url]
    (seq (client-all-names client))))

(defn list-files [url]
  (with-ftp [client url]
    (seq (client-file-names client))))

(defn list-directories [url]
  (with-ftp [client url]
    (seq (client-directory-names client))))

;; this method encrypts the channel when you are using ftps.
;; to avoid error :
;; 425-Server requires protected data connection.
;; 425 Can't open data connection.
;; you must call this before doing a transfer

(defn encrypt-channel [client ]
  (do  (.execPBSZ ^FTPSClient client  0)
       (.execPROT ^FTPSClient client  "P")))

