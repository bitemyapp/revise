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
  (merge q {:query-type :table-list}))

(defn table-create
  [q name & [options]]
  (let [default-options {:primary-key :id
                         :durability :hard
                         :cache-size 1073741824
                         :datacenter nil}
        options (merge default-options options)]

    (merge q {:query-type :table-create
              :options options
              :table name})))

(defn table-drop
  [q name]
  (merge q {:query-type :table-drop
            :table name}))
