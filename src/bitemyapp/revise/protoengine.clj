(ns bitemyapp.revise.protoengine
  "Turn query maps into protobufs"
  (:require [flatland.protobuf.core :refer [protobuf]]
            [bitemyapp.revise.protodefs :refer [Datum Term AssocPair AssocPairTerm]]
            [bitemyapp.revise.utils.case :refer [lower-case]])
  (:import [flatland.protobuf PersistentProtocolBufferMap]))

(defn protobuf?
  [x]
  (instance? PersistentProtocolBufferMap x))

(def primitives
  #{:R_STR :R_NUM :R_NULL :R_BOOL})

(def datums
  #{:R_STR :R_NUM :R_NULL :R_BOOL :R_ARRAY :R_OBJECT})

(defmulti compile-term
  (fn [m]
    (let [t (:bitemyapp.revise.query/type m)]
      (cond
       (= :R_OBJECT t) :obj
       (= :R_ARRAY t) :array
       (primitives t) :primitive
       (= :var t) :var
       (= :args t) :args
       (= :optargs t) :optargs
       (not (nil? t)) :op))))

(defmethod compile-term :default
  [_]
  (throw (Exception. "wat")))

(defmethod compile-term :primitive
  [{type :bitemyapp.revise.query/type
    value :bitemyapp.revise.query/value}]
  (if (= :R_NULL type)
    (protobuf Datum
              :type :R_NULL)
    (protobuf Datum
              :type type
              (lower-case type) value)))

(defmethod compile-term :obj
  [{type :bitemyapp.revise.query/type
    value :bitemyapp.revise.query/value}]
  (protobuf Datum
            :type type
            (lower-case type)
            (mapv (fn [{:keys [key val]}]
                    (protobuf AssocPair
                              :key key
                              :val (compile-term val)))
                  value)))

(defmethod compile-term :array
  [{type :bitemyapp.revise.query/type
    value :bitemyapp.revise.query/value}]
  (protobuf Datum
            :type type
            (lower-case type) (mapv compile-term value)))

(defmethod compile-term :var
  [{type :bitemyapp.revise.query/type
    number :bitemyapp.revise.query/number}]
  (protobuf Term
            :type :VAR
            :args [(protobuf Term
                             :type :DATUM
                             :datum (compile-term number))]))

(defmethod compile-term :args
  [{type :bitemyapp.revise.query/type
    value :bitemyapp.revise.query/value}]
  (mapv (fn [v]
          (if (datums (:bitemyapp.revise.query/type v))
            (protobuf Term
                      :type :DATUM
                      :datum (compile-term v))
            (compile-term v)))
        value))

(defmethod compile-term :optargs
  [{type :bitemyapp.revise.query/type
    value :bitemyapp.revise.query/value}]
  (mapv (fn [[k v]]
          (protobuf AssocPairTerm
                    :key k
                    :val
                    (if (datums (:bitemyapp.revise.query/type v))
                      (protobuf Term
                                :type :DATUM
                                :datum (compile-term v))
                      (compile-term v))))
        value))

(defmethod compile-term :op
  [{type :bitemyapp.revise.query/type
    args :bitemyapp.revise.query/args
    optargs :bitemyapp.revise.query/optargs}]
  (cond
   (and args optargs)
   (protobuf Term
             :type type
             :args (compile-term args)
             :optargs (compile-term optargs))
   args
   (protobuf Term
             :type type
             :args (compile-term args))
   optargs
   (protobuf Term
             :type type
             :optargs (compile-term optargs))
   :else
   (protobuf Term
             :type type)))
