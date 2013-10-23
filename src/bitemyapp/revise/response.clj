(ns bitemyapp.revise.response
  "Doing stuff with the response?")

(defmulti response
  "Inner response"
  (fn [x]
    (cond (vector? x) :vec
          (map? x) (:type x))))

(defmethod response
  :vec
  [v]
  (map response v))

(defmethod response
  :r-array
  [m]
  (map response (:r-array m)))

(defmethod response
  :r-str
  [m]
  (:r-str m))

(defmethod response
  :r-num
  [m]
  (:r-num m))

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
