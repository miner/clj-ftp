(defproject com.velisco/clj-ftp "0.2.0"
  :description "Clojure wrapper on Apache Commons Net for FTP"
  :url "http://github.com/miner/clj-ftp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [me.raynes/fs "1.4.2"]
                 [commons-net "3.1"]]
  :profiles {:test {:resource-paths ["test-resources"]
                    :dependencies [[digest "1.4.3"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}})

