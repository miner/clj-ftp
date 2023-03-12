(defproject com.velisco/clj-ftp "0.3.17"
  :description "Clojure wrapper on Apache Commons Net for FTP"
  :url "http://github.com/miner/clj-ftp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [commons-net "3.9.0"]]
  :profiles {:test {:resource-paths ["test-resources"]
                    :dependencies [[org.mockftpserver/MockFtpServer "3.1.0"]
                                   [org.slf4j/slf4j-jdk14 "1.7.36"]
                                   [digest "1.4.10"]]}})


