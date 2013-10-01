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
  (mapcat response v))

(defmethod response
  :r-array
  [m]
  (map response (:r-array m)))

(defmethod response
  :r-str
  [m]
  (:r-str m))

(defmulti initial
  "Outer response"
  :type)

(defmethod initial :success-atom
  [pb]
  {:token (:token pb)
   :response (response (:response pb))})

(defmethod initial :client-error
  [pb]
  {:token (:token pb)
   :response (response (:response pb))
   :backtrace (:backtrace pb)})

(defmethod initial :runtime-error
  [pb]
  {:token (:token pb)
   :response (response (:response pb))
   :backtrace (:backtrace pb)})

(defmethod initial :compile-error
  [pb]
  {:token (:token pb)
   :response (response (:response pb))
   :backtrace (:backtrace pb)})

(defn inflate
  "Deserialize the response protobuffer"
  [pb]
  (initial pb))
