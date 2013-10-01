(ns bitemyapp.revise.query
  "Query functions"
  (:require [flatland.protobuf.core :refer [protobuf]]
            [bitemyapp.revise.protodefs :refer [Datum Term]]))

;; TODO - make these functions return actual maps. Then turn those maps into protobufs
;; when executing

(defn db
  "Provides a reference to a db. Used as the first argument to some queries I guess."
  [db]
  (protobuf Term
            :type :DB
            :args [(protobuf Term
                             :type :DATUM
                             :datum (protobuf Datum
                                              :type :R_STR
                                              :r_str "test"))]))

(defn table-list
  "The table list of the db"
  [db]
  (protobuf Term
            :type :TABLE_LIST
            :args [db]))
