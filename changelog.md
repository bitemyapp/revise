# 0.0.3

* Fixed a bug where `(group-by .. {:avg :smthing})` would return the error:
`"Cannot divide by 0"`
* On the same note `(group-by .. {:sum :smthing}` no longer returns 0
* Revise now supports all RDB queries!
