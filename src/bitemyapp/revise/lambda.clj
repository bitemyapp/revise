(ns bitemyapp.revise.lambda
  "DSL to create lambda maps to turn into rethinkdb lambdas.")

(declare parse-sexpr parse-thing)

(defmulti parse-thing
  (fn [indexed-args x]
    (cond (seq? x)
          :seq
          (number? x)
          :number
          (vector? x)
          :vec
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

(defmethod parse-thing :vec
  [indexed-args v]
  {:type :array
   :contents (mapv (partial parse-thing index-args) v)})

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

(def dsl-symbols
  '#{;; Comparison
     < <= > >= = not=
     ;; Numeric
     + - / * mod not
     ;; Queries
     contains? has-fields? with-fields keys
     ;; Sequence operations
     pluck without do default update replace delete
     ;; Type inspection
     coerce-to type merge append prepend difference
     ;; Set operations
     set-insert set-union set-insert set-union set-intersection set-difference
     ;; Access
     get get-in nth match empty? indexes-of slice skip limit between distinct
     ;; HOFs
     reduce map filter concat-map order-by group-by for-each
     ;; Result operations
     count union inner-join outer-join eq-join zip grouped-map-reduce info
     ;; Array operations
     insert-at splice-at delete-at change-at sample
     ;; Time
     ->iso8601 ->epoch-time during date time-of-day timezone year month day
     day-of-week day-of-year hours minutes seconds in-timezone
     })

(defn dsl-symbol?
  [s]
  (boolean (dsl-symbols s)))

(defmulti parse-sexpr
  (fn [indexed-args [s & args]]
    s))

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
