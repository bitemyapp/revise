(ns bitemyapp.revise.lambda
  "DSL to create lambda maps to turn into rethinkdb lambdas.")

(declare parse-sexpr parse-thing)

(defmulti parse-thing
  (fn [indexed-args x]
    (cond (seq? x)
          :seq
          (number? x)
          :number
          (string? x)
          :str
          (or (false? x) (true? x))
          :bool
          (symbol? x)
          :sym)))

(defmethod parse-thing :number
  [_ n]
  {:type :number
   :value n})

(defmethod parse-thing :str
  [_ s]
  {:type :string
   :value s})

(defmethod parse-thing :bool
  [_ x]
  {:type :boolean
   :value x})

(defmethod parse-thing :sym
  [indexed-args s]
  (do
    (if (contains? indexed-args s)
      {:type :var
       :number (indexed-args s)}
      ;; Doesnt need the lambda args here
      `(parse-thing {} ~s))))

(defmethod parse-thing :seq
  [indexed-args l]
  (parse-sexpr indexed-args l))

(def dsl
  '{< :lt
    <= :le
    > :gt
    >= :ge

    + :add
    - :sub
    / :div
    * :mul

    or :or
    and :and})

(defn dsl-symbol?
  [s]
  (contains? dsl s))

(defn parse-sexpr
  [indexed-args [s & args]]
  (if (dsl-symbol? s)
    {:type :fn
     :f (dsl s)
     :args (mapv (partial parse-thing indexed-args) args)}
    ;; Assume the sexpr can be evaluated before sending it to the db
    ;; as it shouldn't depend on any lambda args
    `(parse-thing {} (~s ~@args))))

(defn index-args
  [arglist]
  (into {}
        (map vector
             arglist
             (map inc (range)))))

(defmacro lambda
  [arglist & body]
  (let [indexed (index-args arglist)]
    {:type :lambda
     :args (count indexed)
     :body (parse-thing indexed (last body))}))
