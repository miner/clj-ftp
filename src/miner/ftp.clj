
;; Latest Commons Net Update:
;; http://commons.apache.org/net/api-3.1/org/apache/commons/net/ftp/FTPClient.html

;; Uses Apache Commons Net 3.1.  Does not support SFTP.

;; FTP is considered insecure.  Data and passwords are sent in the
;; clear so someone could sniff packets on your network and discover
;; your password.  However, FTP access is useful for dealing with anonymous
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

(defmacro with-ftp [[client url & extra-bindings] & body]
  `(let [u# (io/as-url ~url)
         ^FTPClient ~client (open u#)
         ~@extra-bindings]
     (when ~client
       (try 
         (if (.getUserInfo u#)
           (let [[^String uname# ^String pass#] (.split (.getUserInfo u#) ":" 2)]
             (.login ~client uname# pass#)))
         (.changeWorkingDirectory ~client (.getPath u#))
         (.setFileType ~client FTP/BINARY_FILE_TYPE)
         ~@body
         (catch IOException e# (println "Error:" (.getMessage e#)) nil)
         (finally (when (.isConnected ~client)
                    (try 
                      (.disconnect ~client)
                      (catch IOException e2# nil))))))))


(defn client-list-all [client]
  (map #(.getName  ^FTPFile %) (.listFiles client)))

(defn client-list-files [client]
  (map #(.getName ^FTPFile %) (filter #(.isFile ^FTPFile %) (.listFiles client))))

(defn client-list-directories [client]
  (map #(.getName ^FTPFile %) (filter #(.isDirectory ^FTPFile %) (.listFiles client))))

(defn client-get
  "Get a file (must be within a with-ftp)"
  ([client fname] (client-get client fname (fs/base-name fname)))
  
  ([client fname local-name]
      (with-open [outstream (java.io.FileOutputStream. (io/as-file local-name))]
        (.retrieveFile ^FTPClient client ^String fname ^java.io.OutputStream outstream))))

(defn client-put
  "Put a file (must be within a with-ftp)"
  ([client fname] (client-put client fname (fs/base-name fname)))
			   
  ([client fname remote] (with-open [instream (java.io.FileInputStream. (io/as-file fname))]
			   (.storeFile ^FTPClient client ^String remote ^java.io.InputStream instream))))

(defn client-cd [client dir]
  (.changeWorkingDirectory ^FTPClient client ^String dir))

(defn client-pwd [client]
  (.printWorkingDirectory ^FTPClient client))

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

;; convience methods for one-shot results

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
    (client-list-all url)))

(defn list-files [url]
  (with-ftp [client url]
    (client-list-files client)))

(defn list-directories [url]
  (with-ftp [client url]
    (client-list-directories client)))

