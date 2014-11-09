;; Apache Commons Net API:
;; http://commons.apache.org/proper/commons-net/javadocs/api-3.1/index.html

;; Uses Apache Commons Net 3.1.  Does not support SFTP.
;; For some unknown reason Apache Commons Net 3.2 was causing a hang for me when putting files so
;; we reverted to 3.1 until we can figure out the problem.

;; FTP is considered insecure.  Data and passwords are sent in the
;; clear so someone could sniff packets on your network and discover
;; your password.  Nevertheless, FTP access is useful for dealing with anonymous
;; FTP servers and situations where security is not an issue.

(ns miner.ftp
  (:import (org.apache.commons.net.ftp FTP FTPClient FTPFile FTPReply)
           (java.net URL URLDecoder)
           (java.io File IOException))
  (:require [me.raynes.fs :as fs]
            [clojure.string :as str]
	    [clojure.java.io :as io]))

(defn open [url]
  (let [^FTPClient client (FTPClient.)
        ^URL url (io/as-url url)]
    (.connect client
              (.getHost url)
              (if (= -1 (.getPort url))
                (.getDefaultPort url)
                (.getPort url)))
    (let [reply (.getReplyCode client)]
      (if (not (FTPReply/isPositiveCompletion reply))
        (do (.disconnect client)
            ;; should log instead of println
            (println "Connection refused")
            nil)
        client))))

(defn decode [url-encoded]
  (if-not url-encoded
    ""
    (URLDecoder/decode url-encoded "UTF-8")))

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


(defmacro with-ftp
  "Establish an FTP connection, bound to client, for the FTP url, and execute the body with
   access to that client connection.  Closes connection at end of body.  Keyword
   options can follow the url in the binding vector.  By default, uses a passive local data
   connection mode and  ASCII file type.
   Use [client url :local-data-connection-mode :active :file-type :binary] to override."
  [[client url & {:keys [local-data-connection-mode file-type]}] & body]
  `(let [local-mode# ~local-data-connection-mode
         u# (io/as-url ~url)
         ~client ^FTPClient (open u#)
         file-type# ~file-type]
     (when ~client
       (try
         (when-let [user-info# (.getUserInfo u#)]
           (let [[uname# pass#] (.split user-info# ":" 2)]
             (.login ~client (decode uname#) (decode pass#))))
         (.changeWorkingDirectory ~client (.getPath u#))
         (client-set-file-type ~client file-type#)
         (.setControlKeepAliveTimeout ~client 300)
         ;; by default (when nil) use passive mode
         (if (= local-mode# :active)
           (.enterLocalActiveMode ~client)
           (.enterLocalPassiveMode ~client))
         ~@body
         (catch IOException e# (println (.getMessage e#)) (throw e#))
         (finally (when (.isConnected ~client)
                    (try
                      (.disconnect ~client)
                      (catch IOException e2# nil))))))))


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
  "Complete the previous command and check the reply code. Throw an expection if
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
      (with-open [outstream (java.io.FileOutputStream. (io/as-file local-name))]
        (.retrieveFile ^FTPClient client ^String fname ^java.io.OutputStream outstream))))

(defn client-get-stream
  "Get a file and return InputStream (must be within a with-ftp). Note that it's necessary to complete
   this command with a call to `client-complete-pending-command` after using the stream."
  [client fname]
  (.retrieveFileStream ^FTPClient client ^String fname))

(defn client-put
  "Put a file (must be within a with-ftp)"
  ([client fname] (client-put client fname (fs/base-name fname)))

  ([client fname remote] (with-open [instream (java.io.FileInputStream. (io/as-file fname))]
			   (.storeFile ^FTPClient client ^String remote ^java.io.InputStream instream))))

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
