(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send])
  (:require [bitemyapp.revise.connection :refer [connect close send]]
            [flatland.protobuf.core :as pb]))

(import Rethinkdb$Term)

(def Term (pb/protodef Rethinkdb$Term))

(declare coerce)
(declare coerce-seq)
(declare coerce-map)

(defn coerce-seq [data-seq]
  (map coerce data-seq))

(defn coerce-map [data-map]
  (let [data-type (:type data-map)]
    (cond
     (= data-type :r-array) (coerce-seq (:r-array data-map))
     (= data-type :r-str) (:r-str data-map)
     :else (do
             (println data-map)
             (println data-type)
             (throw (Throwable. "I still don't know"))))))

(defn coerce [data]
  (let [data-type (type data)]
    (cond
     (seq? data) (coerce-seq data)
     (= data-type clojure.lang.PersistentVector) (coerce-seq data)
     (= data-type clojure.lang.PersistentArrayMap) (coerce-map data)
     (= data-type flatland.protobuf.PersistentProtocolBufferMap) (coerce-map data)
     :else (do
             (println data)
             (println data-type)
             (throw (Throwable. "Fuck I don't know."))))))

(defn coerce-response [response]
  (flatten (coerce (:response response))))

(def list-example (pb/protobuf Term :type :DB_LIST))

(defn -main
  []
  (connect)
  (println (send list-example))
  (close))
