(ns bitemyapp.revise.protoengine
  "Turn query maps into protobufs"
  (:require [flatland.protobuf.core :refer [protobuf]]
            [bitemyapp.revise.protodefs :refer [Datum Term]]))

(defn use-db
  [db]
  (protobuf Term
            :type :DB
            :args [(protobuf Term
                             :type :DATUM
                             :datum (protobuf Datum
                                              :type :R_STR
                                              :r_str db))]))

(defn select
  "Select operations"
  [q]
  (case (:select-type (:options q))
    :table-list
    (protobuf Term
              :type :TABLE_LIST
              :args [(use-db (:db q))])))

(defn ->proto
  "Turn a query map into protocol buffers"
  [query]
  (case (:query-type query)
    :db_create nil
    :db_drop nil
    :db_list nil

    :table_create nil
    :table_drop nil
    :table_list nil
    :index_create nil
    :index_drop nil
    :index_list nil

    :insert nil
    :update nil
    :replace nil
    :delete nil

    ;; Unsure about this still
    :select (select query)
    :transform nil
    :aggregate nil
    :document nil
    :math nil
    :logic nil
    :string nil
    :date nil
    :control nil))
