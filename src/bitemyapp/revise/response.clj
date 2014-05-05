(ns bitemyapp.revise.response
  "Parsing a response from rethinkdb")

(defmulti response
  "Inner response"
  (fn [x]
    (cond (vector? x) :vec
          (map? x) (:type x))))

(defmethod response
  :vec
  [v]
  (map response v))

;;; On null values, usually selecting an empty table?
(defmethod response
  :default
  [x]
  nil)

(defmethod response
  :r-array
  [m]
  (mapv response (:r-array m)))

(defmethod response
  :r-str
  [m]
  (:r-str m))

(defn ends-in-0 [n]
  (contains? #{0.0 0} (mod n 1)))

(defn maybe-int [n]
  (or (and (ends-in-0 n) (int n)) n))

(defmethod response
  :r-num
  [m]
  (maybe-int (:r-num m)))

(defmethod response
  :r-bool
  [m]
  (:r-bool m))

(defmethod response
  :r-object
  [m]
  (zipmap (map (comp keyword :key) (:r-object m))
          (map (comp response :val) (:r-object m))))

(defmethod response
  :r-null
  [m]
  nil)

(defmulti initial
  "Outer response"
  :type)

(defmethod initial :success-atom
  [pb]
  {:token (:token pb)
   :response (response (:response pb))})

(defmethod initial :success-sequence
  [pb]
  {:token (:token pb)
   :response (response (:response pb))})

(defmethod initial :success-partial
  [pb]
  {:token (:token pb)
   :response (response (:response pb))})

(defmethod initial :client-error
  [pb]
  {:error :client-error
   :token (:token pb)
   :response (response (:response pb))
   :backtrace (:backtrace pb)})

(defmethod initial :runtime-error
  [pb]
  {:error :runtime-error
   :token (:token pb)
   :response (response (:response pb))
   :backtrace (:backtrace pb)})

(defmethod initial :compile-error
  [pb]
  {:error :compile-error
   :token (:token pb)
   :response (response (:response pb))
   :backtrace (:backtrace pb)})

(defn inflate
  "Deserialize the response protobuffer"
  [pb]
  (initial pb))
