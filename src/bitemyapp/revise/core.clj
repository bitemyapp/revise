(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send])
  (:require [bitemyapp.revise.connection :refer [connect close send]]
            [bitemyapp.revise.protoengine :refer [->proto]]
            [bitemyapp.revise.query :refer [new-query db table-list]]))

(defn run
  [q]
  (send (->proto q)))

(defn -main
  []
  (connect)
  (println (-> (new-query) (db "test") (table-list)))
  (println (-> (new-query) (db "test") (table-list) (run)))
  (close))
