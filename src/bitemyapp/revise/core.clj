(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send])
  (:require [bitemyapp.revise.connection :refer [connect close send]]
            [flatland.protobuf.core :refer [protobuf]]
            [bitemyapp.revise.protodefs :refer [Datum Term]]
            [bitemyapp.revise.query :refer [db table-list]]))

(def list-example (protobuf Term :type :DB_LIST))

(defn -main
  []
  (connect)
  (println (send list-example))
  (println (send (-> (db "test") (table-list))))
  (close))
