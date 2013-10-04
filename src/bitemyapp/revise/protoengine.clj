(ns bitemyapp.revise.protoengine
  "Turn query maps into protobufs"
  (:require [flatland.protobuf.core :refer [protobuf]]
            [bitemyapp.revise.protodefs :refer [Datum Term AssocPair]]
            [bitemyapp.revise.utils.case :refer [snake-case]])
  (:import [flatland.protobuf PersistentProtocolBufferMap]))

(defn protobuf?
  [x]
  (instance? PersistentProtocolBufferMap x))

(defmulti pb-args
  "Make the protobuffer for the args list"
  (fn [x]
    (cond (protobuf? x) :proto
          (string? x) :str
          (keyword? x) :key
          (sequential? x) :seq
          (map? x) :map ; for optargs
          (number? x) :number
          (or (true? x) (false? x)) :bool
          (nil? x) :nil)))

(defmethod pb-args :proto
  [pb]
  pb)

(defmethod pb-args :key
  [k]
  (pb-args (name k)))

(defmethod pb-args :str
  [s]
  (protobuf Term
            :type :DATUM
            :datum (protobuf Datum
                             :type :R_STR
                             :r_str s)))

;; for optargs
(defmethod pb-args :map
  [m]
  (let [terms (map pb-args (vals m))
        ks (map snake-case (keys m))]
    (vec (for [[k v] m :when v :let [snaked (snake-case k)
                                     term (pb-args v)]]
           (protobuf AssocPair
                     :key snaked
                     :val term)))))

(defmethod pb-args :seq
  [coll]
  (mapv pb-args coll))

(defmethod pb-args :number
  [n]
  (let [n (double n)] ;protobuffer says r_num is double
    (protobuf Term
              :type :DATUM
              :datum (protobuf Datum
                               :type :R_NUM
                               :r_num n))))

(defmethod pb-args :bool
  [b]
  (protobuf Term
              :type :DATUM
              :datum (protobuf Datum
                               :type :R_BOOL
                               :r_bool b)))

(defmethod pb-args :nil
  [_]
  (protobuf Term
              :type :DATUM
              :datum (protobuf Datum
                               :type :R_NULL)))

(defn pb-term
  [t args & [optargs]]
  (if optargs
    (protobuf Term
              :type t
              :args (pb-args args)
              :optargs (pb-args optargs))
    (protobuf Term
              :type t
              :args (pb-args args))))

(defn use-db
  [db]
  (pb-term :DB db))

(defn table-list
  [q]
  (pb-term :TABLE_LIST [(use-db (:db q))]))

(defn table-create
  [q]
  (let [{:keys [datacenter primary-key cache-size durability]} (:options q)]
    (pb-term :TABLE_CREATE
             [(use-db (:db q)) (:table q)]
             {:datacenter datacenter
              :primary-key primary-key
              :cache-size cache-size
              :durability durability})))

(defn table-drop
  [q]
  (pb-term :TABLE_DROP
           [(use-db (:db q)) (:table q)]))

(defn ->proto
  "Turn a query map into protocol buffers"
  [query]
  (case (:query-type query)
    :db-create nil
    :db-drop nil
    :db-list nil

    :table-create (table-create query)
    :table-drop (table-drop query)
    :table-list (table-list query)
    :index-create nil
    :index-drop nil
    :index-list nil

    :insert nil
    :update nil
    :replace nil
    :delete nil

    :select nil
    :transform nil
    :aggregate nil
    :document nil
    :math nil
    :logic nil
    :string nil
    :date nil
    :control nil))
