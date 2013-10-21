(ns bitemyapp.revise.protoengine
  "Turn query maps into protobufs"
  (:require [flatland.protobuf.core :refer [protobuf]]
            [bitemyapp.revise.protodefs :refer [Datum Term AssocPair]]
            [bitemyapp.revise.utils.case :refer [snake-case]])
  (:import [flatland.protobuf PersistentProtocolBufferMap]))

(defn protobuf?
  [x]
  (instance? PersistentProtocolBufferMap x))

(def primitives
  #{:R_STR :R_NUM :R_NULL :R_BOOL})

(defmulti compile
  (fn [m]
    (let [t (:type m)]
      (cond
       (= :R_OBJECT t) :obj
       (= :R_ARRAY t) :array
       (primitives t) :primitive
       (= :lambda t) :lambda
       (= :optargs t) :optargs
       (not (nil? t)) :op
       :else (throw (Exception. "wat"))))))

(defmethod compile :primitive
  [{:keys [type value]}]
  (protobuf Term
            :type :DATUM
            :datum (protobuf Datum
                             :type type
                             type value)))

(defmethod compile :optargs
  [{:keys [type value]}]
  (mapv (fn [{:keys [key val]}]
          (protobuf AssocPair
                    :key key
                    :val (compile val)))))

(defmethod compile :op
  [{:keys [type args optargs]}]
  (if optargs
    (protobuf Term
              :type type
              :args (compile args)
              :optargs (compile optargs))))
