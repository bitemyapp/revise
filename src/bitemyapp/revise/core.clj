(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send compile])
  (:require [bitemyapp.revise.connection :as conn :refer [connect send-term]]
            [bitemyapp.revise.protoengine :refer [compile-term]]
            [bitemyapp.revise.query :as r]
            [clojure.core.async :as async :refer [chan <!! >!! alts!!]]
            [bitemyapp.revise.utils.seq :refer [join]]))

(defn run-async
  [q conn]
  (send-term (compile-term q) conn))

(declare full-result)

(defn run
  ([q conn]
     ;; timeout-ms defaults to 10 seconds
     (run q conn 10000))
  ([q conn timeout]
     (let [error (agent-error conn)]
       (when error
         (throw error)))
     (let [channel (run-async q conn)
           t (async/timeout timeout)
           ;; timeout , default 10 seconds
           [result c] (alts!! [t channel])
           error (agent-error conn)]
       (cond (= c t)
             (throw
              (Exception.
               "Timeout on query result, did your connection fail?"))
             (and (not result) error)
             (throw error)
             (instance? java.lang.Exception result)
             (throw result)
             :else
             (full-result conn result)))))

;; TODO - replace the lazy-seq with reducers?
(defn full-result
  [connection starting-result]
  (let [token (:token starting-result)]
    (assert token)
    (if (and (= :success (:type starting-result))
             (= :success-partial (:success starting-result)))
      {:type :success
       :token token
       :success :success-lazy
       :response
       (->>
        ((fn step [prev]
           (lazy-seq
            (if (= :success-partial (:success prev))
              (let [next (<!! (conn/continue token connection))]
                (cons (:response next)
                      (step next)))))) starting-result)
        (cons (:response starting-result))
        (join))}
      starting-result)))
