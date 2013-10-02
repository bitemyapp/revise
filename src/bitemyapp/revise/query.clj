(ns bitemyapp.revise.query
  "Query functions. Return query maps."
  (:require [flatland.protobuf.core :refer [protobuf]]
            [bitemyapp.revise.protodefs :refer [Datum Term]]))

(defn new-query
  []
  {:db nil
   :table nil
   :query-type nil
   :options nil})

(defn db
  [q name]
  (merge q {:db name}))

(defn table
  [q name]
  (merge q {:table name}))

(defn table-list
  [q]
  (merge q {:query-type :select
            :options {:select-type :table-list}}))
