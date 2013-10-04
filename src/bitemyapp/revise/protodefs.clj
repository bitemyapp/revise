(ns bitemyapp.revise.protodefs
  "Protodefinitions assemble!"
  (:require [flatland.protobuf.core :refer [protodef]]))

(import Rethinkdb$VersionDummy)
(import Rethinkdb$Response)
(import Rethinkdb$Query)
(import Rethinkdb$Datum)
(import Rethinkdb$Term)
(import Rethinkdb$Backtrace)
(import Rethinkdb$Frame)
(import Rethinkdb$Term$AssocPair)

(def VersionDummy (protodef Rethinkdb$VersionDummy))
(def Response (protodef Rethinkdb$Response))
(def Datum (protodef Rethinkdb$Datum))
(def Query (protodef Rethinkdb$Query))
(def Frame (protodef Rethinkdb$Frame))
(def Backtrace (protodef Rethinkdb$Backtrace))
(def Term (protodef Rethinkdb$Term))
(def AssocPair (protodef Rethinkdb$Term$AssocPair))
