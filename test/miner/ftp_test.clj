(ns miner.ftp-test
  (:use clojure.test
        miner.ftp)
  (:require [me.raynes.fs :as fs]
            [digest :as dig]
            [clojure.java.io :as io]
            [miner.mock-ftp :as mock-ftp])
  (:import (org.apache.commons.net.ftp FTPFile)))

;;; Note to future testers:  many of these tests are using public FTP servers that were
;;; documented to be available at the time these tests were written.  However, there's no
;;; guarantee that they will continue to be in service and publicly open.  If tests fail, we
;;; may have to try a different server or just temporarily disable the test.

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

(deftest set-connect-timeout
  (let [url "ftp://anonymous@google.com:81"]
    (is (thrown? java.io.IOException (open url "UTF-8" {:connect-timeout-ms 1})))))

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

(deftest write-file
  (with-ftp [client "ftp://anonymous:joe%40mailinator.com@ftp.swfwmd.state.fl.us/pub/incoming"]
    (let [source (.getFile (io/resource "sample.kml"))]
      ;;(println "write-file source = " (when source (.getFile source)))
      (client-put client source (str "s" (System/currentTimeMillis) ".kml")))))

;; FTP server usage: http://cs.brown.edu/system/ftp.html
;; But doesn't seem to work anymore.
;; Switch to DLPTest which allows writes. See https://dlptest.com/ftp-test/

(deftest write-file2
  (with-ftp [client "ftp://dlpuser:rNrKYTX9g7z3RgJRmxWuGHbeu@ftp.dlptest.com/"]
    (let [source (java.io.FileInputStream. (io/file (io/resource "sample.kml")))]
      ;;(println "write-file source = " (when source (.getFile source)))
      (client-put-stream client source (str "s" (System/currentTimeMillis) ".kml")))))

;; not in service, but http might work?   ftp://anonymous:anything@speedtest.tele2.net

;; old host fails, connection closed,
;; "ftp://wrong-username:wrong-password@ftp.cs.brown.edu/incoming"

;;; BAD part is bogus, rest of URL should work and allow writing temporary file
(deftest invalid-login-fails
  (try
    (with-ftp [client "ftp://BADdlpuser:rNrKYTX9g7z3RgJRmxWuGHbeu@ftp.dlptest.com/"]
      ;; try connecting with an invalid pw/username, to trigger the exception
      )
    (catch Exception e
      (is (= "Unable to login with username: \"BADdlpuser\"."
             (.getMessage e)))
      (is (= (:invalid-user (ex-data e)) "BADdlpuser")))))

;; failing probably due to overuse
;; "ftp://anonymous:brown%40mailinator.com@ftp.cs.brown.edu/MISSING"

(deftest invalid-path-fails
  (try
    (with-ftp [client "ftp://dlpuser:rNrKYTX9g7z3RgJRmxWuGHbeu@ftp.dlptest.com/MISSING"]
      ;; try connecting with an invalid path, to trigger the exception
      )
    (catch Exception e
      (is (= "Unable to change working directory to \"/MISSING\"."
             (.getMessage e)))
      (is (= (:invalid-path (ex-data e)) "/MISSING")))))

(defn sha1 [file-or-url]
  (let [file (io/as-file file-or-url)]
    (if (fs/readable? file)
      (dig/sha-1 file)
      (throw (ex-info (str "Unreadable file " (pr-str file-or-url)) {:file file-or-url})))))

;;; failing write "ftp://anonymous:joe%40mailinator.com@ftp.swfwmd.state.fl.us/pub/incoming"
;;; switch to DLPTest.com
(deftest write-file-binary
  (with-ftp [client "ftp://dlpuser:rNrKYTX9g7z3RgJRmxWuGHbeu@ftp.dlptest.com/"
             :file-type :binary]
    (let [source (io/resource "spacer.jpg")
          dest (str "sp" (System/currentTimeMillis) ".jpg")
          tmp (fs/temp-file "spacer")
          guess (guess-file-type source)]
      (is (= guess :binary))
      (client-set-file-type client guess)
      ;; (println "write-file-binary source = " (when source (.getFile source)))
      ;; (println "write-file-binray source = " dest)
      ;; (println "write-file-binary tmp = " (str tmp))
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


(deftest ftps
  ;; Per http://www.sftp.net/public-online-sftp-servers, which is apparently
  ;; maintained by Rebex developers.
  (is (= ::success
         (with-ftp [client "ftps://demo:password@test.rebex.net:21"
                    :data-timeout-ms 10000, :control-keep-alive-timeout-sec 10
                    :control-keep-alive-reply-timeout-ms 500]
           ::success))))


(deftest user-info-percent-encoding
  (are [x y] (= x (user-info y "UTF-8"))
    nil                    "ftp://example.com"
    ["foo" "bar"]          "ftp://foo:bar@example.com"
    ["foo" "bar"]          "ftps://foo:bar@example.com"

    ["foo" nil]            "ftp://foo@example.com"
    ["" "bar"]             "ftp://:bar@example.com"

    ["foo:bar" "baz"]      "ftp://foo%3abar:baz@example.com"
    ["foo:bar" "baz"]      "ftp://foo%3Abar:baz@example.com"
    ["foo" "bar:baz"]      "ftp://foo:bar%3abaz@example.com"
    ["foo" "bar:baz"]      "ftp://foo:bar%3Abaz@example.com"

    ["foo@bar" "baz@quux"] "ftp://foo%40bar:baz%40quux@example.com"
    ["foo%bar" "baz%quux"] "ftp://foo%25bar:baz%25quux@example.com"

    ["foo++bar" "baz"]     "ftp://foo++bar:baz@example.com"
    ["foo+++" "baz"]       "ftp://foo+++:baz@example.com"

    ["çåƒé" "ßåßê"]        "ftp://%c3%a7%c3%a5%c6%92%c3%a9:%c3%9f%c3%a5%c3%9f%c3%aa@example.com"))


;; Note that the mock account password is "#password".  The first character is the "hash"
;; character (a.k.a. number sign or octothorp), which requires percent encoding in URLs.
;; The equivalent is "%23".
;; https://www.w3schools.com/tags/ref_urlencode.ASP

(deftest default-timeout
  (let [mock-ftp-port 2021
        mock-server (mock-ftp/build mock-ftp-port mock-ftp/control-connection-timeout)]
    (.start mock-server)
    (with-ftp [client (str "ftp://username:%23password@localhost:" mock-ftp-port) :default-timeout-ms 200]
      (is (thrown? java.io.IOException (client-file-names client))))
    (.stop mock-server)))

(deftest explicit-user-credentials
  (let [mock-ftp-port 2021
        mock-server (mock-ftp/build mock-ftp-port)]
    (.start mock-server)
    (with-ftp [client (str "ftp://localhost:" mock-ftp-port) :username "username" :password "#password"]
      (is (empty? (client-file-names client))))
    (.stop mock-server)))

(deftest url-user-credentials
  (let [mock-ftp-port 2021
        mock-server (mock-ftp/build mock-ftp-port)]
    (.start mock-server)
    (with-ftp [client (str "ftp://username:%23password@localhost:" mock-ftp-port)]
      (is (empty? (client-file-names client))))
    (.stop mock-server)))
