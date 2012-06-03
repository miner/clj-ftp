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
      (is (pos? (count (client-list-all client))))
      (client-cd client "emacs")
      (is (.endsWith (client-pwd client) "emacs"))
      (client-get client "README.otherversions" tmp))
    (is (fs/exists? tmp)
    (when (fs/exists? tmp)
      (fs/delete tmp)))))
