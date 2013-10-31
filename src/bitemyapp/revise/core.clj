(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send compile])
  (:require [bitemyapp.revise.connection :refer [connect close send-term]]
            [bitemyapp.revise.protoengine :refer [compile-term]]
            [bitemyapp.revise.query :as r]))

(defn run
  [q]
  (send-term (compile-term q)))

(defn -main
  []
  (connect)
  (prn (-> (r/db "test") (r/table-list-db) (run)))
  (close))
