
;; Latest Commons Net Update:
;; http://commons.apache.org/net/api-3.1/org/apache/commons/net/ftp/FTPClient.html

;; Uses Apache Commons Net 3.1.  Does not support SFTP.
;; For some unknown reason Apache Commons Net 3.2 was causing a hang for me when putting files so
;; we reverted to 3.1 until we can figure out the problem.

;; FTP is considered insecure.  Data and passwords are sent in the
;; clear so someone could sniff packets on your network and discover
;; your password.  Nevertheless, FTP access is useful for dealing with anonymous
;; FTP servers and situations where security is not an issue.

(ns miner.ftp
  (:import (org.apache.commons.net.ftp FTP FTPClient FTPFile FTPReply)
           (java.net URL)
           (java.io File IOException))
  (:require [fs.core :as fs]
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

(defmacro with-ftp [[client url] & body]
  `(let [u# (io/as-url ~url)
         ^FTPClient ~client (open u#)]
     (when ~client
       (try
         (when-let [user-info# (.getUserInfo u#)]
           (let [[^String uname# ^String pass#] (.split user-info# ":" 2)]
             (.login ~client uname# pass#)))
         (.changeWorkingDirectory ~client (.getPath u#))
         (.setFileType ~client FTP/BINARY_FILE_TYPE)
         (.setControlKeepAliveTimeout ~client 300)
         (.enterLocalPassiveMode ~client)
         ~@body
         (catch IOException e# (println (.getMessage e#)) (throw e#))
         (finally (when (.isConnected ~client)
                    (try
                      (.disconnect ~client)
                      (catch IOException e2# nil))))))))


(defn client-list-all [client]
  "DEPRECATED use client-all-names"
  (map #(.getName  ^FTPFile %) (.listFiles client)))

(defn client-list-files [client]
  "DEPRECATED use client-file-names"
  (map #(.getName ^FTPFile %) (filter #(.isFile ^FTPFile %) (.listFiles client))))

(defn client-list-directories [client]
  "DEPRECATED use client-directory-names"
  (map #(.getName ^FTPFile %) (filter #(.isDirectory ^FTPFile %) (.listFiles client))))

(defn client-FTPFiles-all [client]
  (vec (.listFiles client)))

(defn client-FTPFiles [client] 
  (filterv (fn [f] (and f (.isFile ^FTPFile f))) (.listFiles client)))

(defn client-FTPFile-directories [client]
  (vec (.listDirectories client)))

(defn client-all-names [client] 
  (vec (.listNames client)))
     
(defn client-file-names [client] 
  (mapv #(.getName ^FTPFile %) (client-FTPFiles client)))

(defn client-directory-names [client] 
  (mapv #(.getName ^FTPFile %) (client-FTPFile-directories client)))

(defn client-get
  "Get a file and write to local file-system (must be within a with-ftp)"
  ([client fname] (client-get client fname (fs/base-name fname)))

  ([client fname local-name]
      (with-open [outstream (java.io.FileOutputStream. (io/as-file local-name))]
        (.retrieveFile ^FTPClient client ^String fname ^java.io.OutputStream outstream))))

(defn client-get-stream
  "Get a file and return InputStream (must be within a with-ftp)"
  [client fname]
  (.retrieveFileStream ^FTPClient client ^String fname))

(defn client-put
  "Put a file (must be within a with-ftp)"
  ([client fname] (client-put client fname (fs/base-name fname)))

  ([client fname remote] (with-open [instream (java.io.FileInputStream. (io/as-file fname))]
			   (.storeFile ^FTPClient client ^String remote ^java.io.InputStream instream))))

(defn client-cd [client dir]
  (.changeWorkingDirectory ^FTPClient client ^String dir))

(defn- strip-double-quotes [s]
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
    (seq (client-all-names url))))

(defn list-files [url]
  (with-ftp [client url]
    (seq (client-file-names client))))

(defn list-directories [url]
  (with-ftp [client url]
    (seq (client-directory-names client))))
