(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send compile])
  (:require [bitemyapp.revise.connection :refer [connect close send-term]]
            [bitemyapp.revise.protoengine :refer [compile-term]]
            [bitemyapp.revise.query :as r]))

(defn run-async
  [q conn]
  (send-term (compile-term q) conn))

(defn run
  ([q conn]
     ;; timeout-ms defaults to 10 seconds
     (run q conn 10000))
  ([q conn timeout]
     (let [error (agent-error conn)]
       (when error
         (throw error)))
     (let [prom (run-async q conn)
           ;; timeout , default 10 seconds
           result (deref prom timeout nil)
           error (agent-error conn)]
       (when (and (not result) error)
         (throw error))
       (when-not result
         (throw
          (Exception.
           "Timeout on query result, did your connection fail?")))
       (if (instance? java.lang.Exception result)
         (throw result)
         result))))
