(ns miner.mock-ftp
  (:import
    (org.mockftpserver.fake UserAccount)
    (org.mockftpserver.fake.filesystem UnixFakeFileSystem DirectoryEntry)
    (org.mockftpserver.fake.command AbstractFakeCommandHandler)
    (org.mockftpserver.core.command CommandNames)
    (org.mockftpserver.fake FakeFtpServer)))

(def control-connection-timeout
  (-> (proxy [AbstractFakeCommandHandler] []
        (handle [cmd session]
          (while true
            (Thread/sleep 60000))))))

(defn build 
  (^FakeFtpServer [control-port]
   (build control-port nil))
  (^FakeFtpServer [control-port handler]
   (let [mock-server (new FakeFtpServer)
         filesystem (new UnixFakeFileSystem)]
     (.addUserAccount mock-server (new UserAccount "username" "password" "/home/username"))
     (.add filesystem (new DirectoryEntry "/home/username"))
     (.setFileSystem mock-server filesystem)

     (.setServerControlPort mock-server control-port)
     (when handler
       (.setCommandHandler mock-server CommandNames/PASV handler))
     mock-server)))
