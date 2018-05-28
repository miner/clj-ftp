(defproject com.velisco/clj-ftp "0.3.12"
  :description "Clojure wrapper on Apache Commons Net for FTP"
  :url "http://github.com/miner/clj-ftp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [commons-net "3.6"]]
  :profiles {:test {:resource-paths ["test-resources"]
                    :dependencies [[org.mockftpserver/MockFtpServer "2.0.2"]
                                   [digest "1.4.8"]]}})


