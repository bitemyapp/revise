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

(defmulti compile
  (fn [m]
    (let [t (:type m)]
      (cond
       (= :R_OBJECT t) :obj
       (= :R_ARRAY t) :array
       (primitives t) :primitive
       (= :var t) :var
       (= :args t) :args
       (= :optargs t) :optargs
       (not (nil? t)) :op))))

(defmethod compile :default
  [_]
  (prn _)
  (throw (Exception. "wat")))

(defmethod compile :primitive
  [{:keys [type value]}]
  (if (= :R_NULL type)
    (protobuf Datum
              :type :R_NULL)
    (protobuf Datum
              :type type
              (lower-case type) value)))

(defmethod compile :obj
  [{:keys [type value]}]
  (protobuf Datum
            :type type
            (lower-case type)
            (mapv (fn [{:keys [key val]}]
                    (protobuf AssocPair
                              :key key
                              :val (compile val)))
                  value)))

(defmethod compile :array
  [{:keys [type value]}]
  (protobuf Datum
            :type type
            (lower-case type) (mapv compile value)))

(defmethod compile :var
  [{:keys [type number]}]
  (protobuf Term
            :type :VAR
            :args [(protobuf Term
                             :type :DATUM
                             :datum (compile number))]))

(defmethod compile :args
  [{:keys [type value]}]
  (mapv (fn [v]
          (if (datums (:type v))
            (protobuf Term
                      :type :DATUM
                      :datum (compile v))
            (compile v)))
        value))

(defmethod compile :optargs
  [{:keys [type value]}]
  (prn value)
  (mapv (fn [[k v]]
          (protobuf AssocPairTerm
                    :key k
                    :val (protobuf Term
                                   :type :DATUM
                                   :datum (compile v))))
        value))

(defmethod compile :op
  [{:keys [type args optargs]}]
  (cond
   (and args optargs)
   (protobuf Term
             :type type
             :args (compile args)
             :optargs (compile optargs))
   args
   (protobuf Term
             :type type
             :args (compile args))
   :else
   (protobuf Term
             :type type)))
