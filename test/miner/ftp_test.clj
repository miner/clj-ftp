(ns miner.ftp-test
  (:use clojure.test
        miner.ftp)
  (:require [me.raynes.fs :as fs]
            [digest :as dig]
            [clojure.java.io :as io])
  (:import (org.apache.commons.net.ftp FTPFile)))

(deftest listing
  (is (pos? (count (list-files "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs")))))

(deftest retrieve-file-one-shot
  (let [tmp (fs/temp-file "ftp-")]
    (retrieve-file "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs" "README.otherversions" tmp)
    (is (fs/exists? tmp))
    (when (fs/exists? tmp)
      (fs/delete tmp))))

(defn get-file-guts [client tmpfile]
  (client-cd client "..")
  (is (.endsWith ^String (client-pwd client) "gnu"))
  (is (pos? (count (client-all-names client))))
  (client-cd client "emacs")
  (is (.endsWith ^String (client-pwd client) "emacs"))
  (client-get client "README.otherversions" tmpfile)
  (is (fs/exists? tmpfile)))

(deftest get-file-client
  (let [tmp (fs/temp-file "ftp-")
        tmp2 (fs/temp-file "ftp-")
        url "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs"]
    (with-ftp [client url]
      (get-file-guts client tmp))
    (with-ftp [client2 url :local-data-connection-mode :active]
      (get-file-guts client2 tmp2))
    (is (fs/size tmp) (fs/size tmp2))
    (when (fs/exists? tmp)
      (fs/delete tmp))
    (when (fs/exists? tmp2)
      (fs/delete tmp2))))

(deftest get-stream-client
  (let [tmp (fs/temp-file "ftp-")]
    (with-ftp [client "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs"]
      (is (instance? java.io.InputStream
                     (client-get-stream client "README.olderversions"))))))

(deftest get-stream-client-two-files
  (let [tmp (fs/temp-file "ftp-")]
    (with-ftp [client "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs"]
      (with-open [s1 (client-get-stream client "README.olderversions")]
        (is (instance? java.io.InputStream s1))
        (io/copy s1 tmp)
        (client-complete-pending-command client))
      (with-open [s2 (client-get-stream client "README.olderversions")]
        (is (instance? java.io.InputStream s2))
        (io/copy s2 tmp)
        (client-complete-pending-command client)))))

(deftest get-all
  (with-ftp [client "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu"
             :data-timeout-ms 50000, :control-keep-alive-timeout-sec 10
             :control-keep-alive-reply-timeout-ms 500]
    (is (mapv #(.getName ^FTPFile %) (client-FTPFiles client)) (client-all-names client))))

(defn print-FTPFiles-list [label ftpfiles]
  (println)
  (println label)
  (doseq [^FTPFile f ftpfiles]
    (print (.getName f))
    (when (.isDirectory f) (print "/"))
    (println))
  (println))

(comment
  (with-ftp [client "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu"]
    (print-FTPFiles-list "files only" (client-FTPFiles client))
    (print-FTPFiles-list "dirs only" (client-FTPFile-directories client))
    (print-FTPFiles-list "all" (client-FTPFiles-all client)))
)

;; Writable FTP server usage: http://www.swfwmd.state.fl.us/data/ftp/
;; Server is down so tests have to be disabled
#_ (deftest write-file
  (with-ftp [client "ftp://anonymous:joe%40mailinator.com@ftp.swfwmd.state.fl.us/pub/incoming"]
    (let [source (.getFile (io/resource "sample.kml"))]
      ;;(println "write-file source = " (when source (.getFile source)))
      (client-put client source (str "s" (System/currentTimeMillis) ".kml")))))

;; Writable FTP server usage: http://cs.brown.edu/system/ftp.html
;; OK to upload to incoming, but can't download from there.
(deftest write-file2
  (with-ftp [client "ftp://anonymous:brown%40mailinator.com@ftp.cs.brown.edu/incoming"]
    (let [source (.getFile (io/resource "sample.kml"))]
      ;;(println "write-file source = " (when source (.getFile source)))
      (client-put client source (str "s" (System/currentTimeMillis) ".kml")))))

;; Writable FTP server usage: http://cs.brown.edu/system/ftp.html
;; OK to upload to incoming, but can't download from there.
(deftest write-file3
  (with-ftp [client "ftp://anonymous:brown%40mailinator.com@ftp.cs.brown.edu/incoming"]
    (let [source (java.io.FileInputStream. (io/file (io/resource "sample.kml")))]
      ;;(println "write-file source = " (when source (.getFile source)))
      (client-put-stream client source (str "s" (System/currentTimeMillis) ".kml")))))

(deftest invalid-login-fails
  (try
    (with-ftp [client "ftp://wrong-password:wrong-username@ftp.cs.brown.edu/incoming"]
      ;; try connecting with an invalid pw/username, to trigger the exception
      )
    (catch Exception e
      (is (= "Unable to login with credentials: \"wrong-password\" , \"wrong-username\"."
             (.getMessage e))))))

(defn sha1 [file-or-url]
  (let [file (io/as-file file-or-url)]
    (if (fs/readable? file)
      (dig/sha-1 file)
      (throw (ex-info (str "Unreadable file " (pr-str file-or-url)) {:file file-or-url})))))

#_ (deftest write-file-binary
  (with-ftp [client "ftp://anonymous:joe%40mailinator.com@ftp.swfwmd.state.fl.us/pub/incoming"
             :file-type :binary]
    (let [source (io/resource "spacer.jpg")
          dest (str "sp" (System/currentTimeMillis) ".jpg")
          tmp (fs/temp-file "spacer")
          guess (guess-file-type source)]
      (is (= guess :binary))
      (client-set-file-type client guess)
      ;;(println "write-file source = " (when source (.getFile source)))
      (client-put client source dest)
      (client-get client dest tmp)
      (is (= (fs/size source) (fs/size tmp)))
      ;; test for file corruption that can result from wrong file type
      (is (= (sha1 source) (sha1 tmp)))
      (fs/delete tmp))))


;; ftp://ftp4.us.freebsd.org/pub/FreeBSD/ works for read-only

;; Another possibility: http://user.agu.org/ishelp/ftp.html
;; ftp://ftp.agu.org/incoming/test/
;; pub is the only readable dir but you have to know full filename
