(ns miner.ftp-test
  (:use clojure.test
        miner.ftp)
  (:require [fs.core :as fs]))

(deftest listing
  (is (pos? (count (list-files "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs")))))

(deftest retrieve-file-one-shot
  (let [tmp (fs/temp-file "ftp-")]
    (retrieve-file "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs" "README.otherversions" tmp)
    (is (fs/exists? tmp))
    (when (fs/exists? tmp)
      (fs/delete tmp))))

(deftest get-file-client
  (let [tmp (fs/temp-file "ftp-")]
    (with-ftp [client "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs"]
      (client-cd client "..")
      (is (.endsWith (client-pwd client) "gnu"))
      (is (pos? (count (client-all-names client))))
      (client-cd client "emacs")
      (is (.endsWith (client-pwd client) "emacs"))
      (client-get client "README.otherversions" tmp))
    (is (fs/exists? tmp)
    (when (fs/exists? tmp)
      (fs/delete tmp)))))

(deftest get-stream-client
  (let [tmp (fs/temp-file "ftp-")]
    (with-ftp [client "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs"]
      (is (instance? java.io.InputStream
                     (client-get-stream client "README.olderversions"))))))

(deftest get-filenames
  (with-ftp [client "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu/emacs"]
    (is (client-file-names client) (client-list-files client))))

(deftest get-all
  (with-ftp [client "ftp://anonymous:user%40example.com@ftp.gnu.org/gnu"]
    (is (mapv #(.getName %) (client-FTPFiles client)) (client-all-names client))))

(defn print-FTPFiles-list [label ftpfiles]
  (println)
  (println label)
  (doseq [f ftpfiles]
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
