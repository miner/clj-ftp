# clj-ftp

Wrapper over Apache Commons Net to provide easy access from Clojure.

Note: FTP is considered insecure.  Data and passwords are sent in the
clear so someone could sniff packets on your network and discover
your password.  Nevertheless, FTP access is useful for dealing with anonymous
FTP servers and situations where security is not an issue.

## Available on Clojars

https://clojars.org/com.velisco/clj-ftp

Leiningen dependencies:

	[com.velisco/clj-ftp "0.3.0"]
	
Double check on Clojars in case I forget to update the version number in this document.

## Usage

    (require '[miner.ftp :as ftp])

    (ftp/with-ftp [client "ftp://anonymous:pwd@ftp.example.com/pub"]
		(ftp/client-get client "interesting.txt" "stuff.txt"))
		
By default, we use a passive local data connection.  You can override that by passing an option
after the URL.  Use :local-data-connection-mode :active if you don't want passive mode.  For
example:

    (ftp/with-ftp [client "ftp://anonymous:pwd@ftp.example.com/pub" 
	               :local-data-connection-mode :active]
		(ftp/client-get client "interesting.txt" "stuff.txt"))

The default file-type for transfers is :ascii, but you can change it with the option `:file-type
:binary` in `with-ftp`.  Use `client-set-file-type` to set it appropriately before each transfer.

## License

Copyright © 2012-13 Stephen E. Miner

Distributed under the Eclipse Public License, the same as Clojure.
