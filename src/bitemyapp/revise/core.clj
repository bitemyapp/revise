(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send compile])
  (:require [bitemyapp.revise.connection :refer [connect close send]]
            [bitemyapp.revise.protoengine :refer [compile]]
            [bitemyapp.revise.query :as r]))

(defn run
  [q]
  (send (compile q)))

(defn -main
  []
  (connect)
  (prn (-> (r/db "test") (r/table-list-db) (run)))
  (close))

;; Prints

;; When connecting: SUCCESS
;; {:token 1, :response (("tv_shows"))}
