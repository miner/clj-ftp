# clj-ftp

Wrapper over [Apache Commons Net][apache] to provide easy access from Clojure.

[apache]: https://commons.apache.org/proper/commons-net/ "Apache Commons Net"

Note: FTP is considered insecure.  Data and passwords are sent in the
clear so someone could sniff packets on your network and discover
your password.  Nevertheless, FTP access is useful for dealing with anonymous
FTP servers and situations where security is not an issue.

## Leiningen

*clj-ftp* is available from Clojars.  Add the following dependency to your *project.clj*:

[![clj-ftp on clojars.org][latest]][clojar]

[latest]: https://clojars.org/com.velisco/clj-ftp/latest-version.svg "clj-ftp on clojars.org"
[clojar]: https://clojars.org/com.velisco/clj-ftp


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

The options for `with-ftp` are:
- `:default-timeout-ms` (not set by default)
- `:connect-timeout-ms` (default to 30000)
- `:data-timeout-ms` (default infinite)
- `:control-keep-alive-timeout-sec` (default 300)
- `:control-keep-alive-reply-timeout-ms` (default 1000)
- `:control-encoding` (default "UTF-8")
- `:file-type` (default :ascii)
- `:local-data-connection-mode` (default :passive)
- `:security-mode` (default :explicit)
- `:strict-parsing-mode` (default: true)


## License

Copyright © 2012-21 Stephen E. Miner

Distributed under the Eclipse Public License, the same as Clojure.
