# 0.1.0 - MANY BREAKING CHANGES
* Revise supports Rethinkdb 1.12 (The Wizard of Oz)
* Removed dependency on lein-protobuf. Moved protocol buffers to their own jar.
* Added the following queries: group, sum, avg, min, max, object, upcase, downcase,
split-string, index-status, index-wait, sync
* Removed deprecated queries grouped-map-reduce, group-by
* The result promise is now a core.async channel
* Added mechanism for terms CONTINUE, STOP, WAIT_NOREPLY
* Now supports success-partial queries
* Removed silly `or`, `and` queries as they were duplicates.

# 0.0.6
* Fix missing clojure.walk require in query ns

# 0.0.5
* Added helper queries `or`, `and`

# 0.0.4
* Added connection error handling when sending queries.
* The function `run` from previous versions is now `run-async`.
* The actual `run` now dereferences the promise automatically with a timeout
and throws when it times out or the agent dies. It now takes an optional timeout
which defaults to 10000 (ms)

# 0.0.3
* Fixed a bug where `(group-by .. {:avg :smthing})` would return the error:
`"Cannot divide by 0"`
* On the same note `(group-by .. {:sum :smthing}` no longer returns 0
* Revise now supports all RDB queries!
