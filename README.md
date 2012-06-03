# clj-ftp

Wrapper over Apache Commons Net to provide easy access from Clojure.

Note: FTP is considered insecure.  Data and passwords are sent in the
clear so someone could sniff packets on your network and discover
your password.  However, FTP access is useful for dealing with anonymous
FTP servers and situations where security is not an issue.


## Usage

    (require '[miner.ftp :as ftp])

    (ftp/with-ftp [client "ftp://anonymous:pwd@ftp.example.com/pub"]
		(ftp/client-get client "interesting.txt" "stuff.txt"))
		

## License

Copyright © 2012 Stephen E. Miner

Distributed under the Eclipse Public License, the same as Clojure.
