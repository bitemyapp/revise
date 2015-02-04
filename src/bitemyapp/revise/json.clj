(ns bitemyapp.revise.json
  "Compile the results from bitemyapp.revise.query into JSON."
  (:require [cheshire.core :as js]
            [bitemyapp.revise.ql2 :refer [rethinkdb]]
            [bitemyapp.revise.query :as r]))

(defn compile-query
  "A query is a vector that bitemyapp.revise.query/query returns"
  [query]
  ({:pre [(vector? query)]})
  (let [[type term opts] query
        cnt (count query)]
    (cond (= 1 cnt)
          (js/generate-string [type])
          (= 3 cnt)
          nil
          :else
          (throw (ex-info (pr-str
                           "Wrong number of elements in query. Need 1 or 3"
                           query) {:query query})))))

(def primitives
  #{:R_STR :R_NUM :R_NULL :R_BOOL})

(def datums
  #{:R_STR :R_NUM :R_NULL :R_BOOL :R_ARRAY :R_OBJECT})

(def reserved
  #{:var :args :optargs :R_STR :R_NUM :R_NULL :R_BOOL :R_ARRAY :R_OBJECT})

(defn term-value
  [term-key]
  (get-in rethinkdb [:Term :TermType term-key]))

(defmulti compile-term
  (fn [m]
    (let [t (:bitemyapp.revise.query/type m)]
      (cond
       (= :R_OBJECT t) :obj
       (= :R_ARRAY t) :array
       (primitives t) :primitive
       ; (= :var t) :var
       (= :args t) :args
       (= :optargs t) :optargs
       (not (nil? t)) :op))))

(defmethod compile-term :default
  [x]
  (let [msg (if (nil? x)
              "Cannot compile 'nil' Maybe you messed up namespace qualifying a query?"
              (str "Cannot compile '" x
                   "' Maybe you messed up namespace qualifying a query?"))]
    (throw (Exception.
            msg))))

(defmethod compile-term :primitive
  [{value :bitemyapp.revise.query/value}]
  value)

(defmethod compile-term :obj
  [{value :bitemyapp.revise.query/value}]
  (into {} (for [[k v] value]
             [k (compile-term v)])))

(defmethod compile-term :array
  [{value :bitemyapp.revise.query/value}]
  [(term-value :MAKE_ARRAY) (mapv compile-term value)])

(defmethod compile-term :args
  [{value :bitemyapp.revise.query/value}]
  (mapv compile-term value))

(defmethod compile-term :optargs
  [{value :bitemyapp.revise.query/value}]
  (into {} (for [[]])))
