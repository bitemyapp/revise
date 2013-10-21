(ns bitemyapp.revise.query
  "The clojure REQL API")

;;; -------------------------------------------------------------------------
;;; Utils

(defn map-keys
  [f m]
  (zipmap (keys m)
          (map f (vals m))))

;;; -------------------------------------------------------------------------
;;; DatumTypes parsing

;;; TODO - difference between optargs and objs
;;; Also difference between args and array?
(defn parse-val
  [x]
  (letfn [(dt-map [t val]
            (cond
             (= :R_ARRAY t)
             {:type t
              :value (mapv parse-val val)}
             (= :R_OBJECT t)
             {:type t
              :value (mapv (fn [[k v]]
                             {:key (name k)
                              :val (parse-val v)})
                           val)}
             :else
             {:type t
              :value val}))]
    (if (and (map? x) (:type x))
      x
      (-> (cond (string? x) :R_STR
                (number? x) :R_NUM
                (nil? x) :R_NULL
                (vector? x) :R_ARRAY
                (and (map? x)
                     (not (:type x))) :R_OBJECT
                (or (false? x) (true? x)) :R_BOOL)
          (dt-map x)))))

;;; -------------------------------------------------------------------------
;;; General stuff

(defn query
  ([type]
     {:type type})
  ([type args]
     {:type type
      :args (parse-val (vec args))})
  ([type args optargs-map]
     {:type type
      :args (parse-val (vec args))
      :optargs (parse-val optargs-map)}))

;;; -------------------------------------------------------------------------
;;; Lambdas

(defn index-args
  [lambda-args]
  (zipmap lambda-args
          (map (fn [n]
                 {:type :var
                  :number (inc n)})
               (range))))

(defmacro lambda
  [arglist & body]
  (let [ret (last body)
        arg-replacements (index-args arglist)]
    {:type :lambda
     :arg-count (count arglist)
     ;; TODO - not the best model of scope
     :ret (clojure.walk/postwalk-replace arg-replacements ret)}))

;;; -------------------------------------------------------------------------
;;; Datums

;;; -- Compound types --
;;; Confused by these 2
(defn make-array
  [datum]
  (query :MAKE_ARRAY))

(defn make-obj
  [m]
  (query :MAKE_OBJ))

(defn js
  ([s] (query :JAVASCRIPT [s]))
  ([s timeout] (query :JAVASCRIPT [s] {:timeout timeout})))

(defn error
  ([] (query :ERROR))
  ([s] (query :ERROR [s])))

(defn implicit-var
  []
  (query :IMPLICIT_VAR))

;;; -- Data Operators --
(defn db
  [db-name]
  (query :DB [db-name]))

(defn table
  ([table-name]
     (query :TABLE [table-name]))
  ([table-name use-outdated?]
     (query :TABLE [table-name] {:use_outdated use-outdated?})))
;;; TODO - figure out a better approach for this
(defn table-db
  ([db table-name]
     (query :TABLE [db table-name]))
  ([db table-name use-outdated?]
     (query :TABLE [db table-name] {:use_outdated use-outdated?})))

(defn get
  [table k]
  (let [k (name k)]
    (query :GET [table k])))
;;; todo - check
(defn get-all
  ([table xs]
     (query :GET_ALL xs))
  ([table xs index]
     (query :GET_ALL xs {:index index})))

;;; -- DATUM Ops --
(defn =
  [& args]
  (query :EQ args))

(defn not=
  [& args]
  (query :NE args))

(defn <
  [& args]
  (query :LT args))

(defn <=
  [& args]
  (query :LE args))

(defn >
  [& args]
  (query :GT args))

(defn >=
  [& args]
  (query :GE args))

(defn not
  [bool]
  (query :NOT [bool]))

(defn +
  "Add two numbers or concatenate two strings"
  [& args]
  (query :ADD args))

(defn -
  [& args]
  (query :SUB args))

(defn *
  [& args]
  (query :MUL args))

(defn /
  [& args]
  (query :DIV args))

(defn mod
  [n1 n2]
  (query :MOD [n1 n2]))

;;; -- Datum Array Ops --
(defn append
  [array x]
  (query :APPEND [array x]))

(defn prepend
  [array x]
  (query :PREPEND [array x]))

(defn difference
  [array1 array2]
  (query :DIFFERENCE [array1 array2]))

;;; -- Set Ops --
;;; No actual sets on rethinkdb, only arrays
(defn set-insert
  [array x]
  (query :SET_INSERT [array x]))

(defn set-intersection
  [array1 array2]
  (query :SET_INTERSECTION [array1 array2]))

(defn set-union
  [array1 array2]
  (query :SET_UNION [array1 array2]))

(defn set-difference
  [array1 array2]
  (query :SET_DIFFERENCE [array1 array2]))

(defn slice
  [sq n1 n2]
  (query :SLICE [sq n1 n2]))

(defn skip
  [sq n]
  (query :SKIP [sq n]))

(defn limit
  [sq n]
  (query :LIMIT [sq n]))

(defn indexes-of
  [sq lambda1-or-x]
  (query :INDEXES_OF [sq lambda1-or-x]))

(defn contains?
  [sq lambda1-or-x]
  (query :CONTAINS [sq lambda1-or-x]))

;;; -- Stream/Object Ops --
(defn get-field
  "Get a particular field from an object or map that over a sequence"
  [obj-or-sq s]
  (query :GET_FIELD [obj-or-sq s]))

(defn keys
  "Return an array containing the keys of the object"
  [obj]
  (query :KEYS [obj]))

(defn has-fields?
  "Check whether an object contains all the specified fields or filters a
sequence so that al objects inside of it contain all the specified fields"
  [obj & pathspecs]
  (query :HAS_FIELDS (concat [obj] pathspecs)))

(defn with-fields
  "(with-fields sq pathspecs..) <=> (pluck (has-fields sq pathspecs..) pathspecs..)"
  [sq & pathspecs]
  (query :HAS_FIELDS (concat [sq] pathspecs)))

(defn pluck
  "Get a subset of an object by selecting some attributes to preserve,
or map that over a sequence"
  [obj-or-sq & pathspecs]
  (query :PLUCK (concat [obj-or-sq] pathspecs)))

(defn without
  "Get a subset of an object by selecting some attributes to discard,
or map that over a sequence"
  [obj-or-sq & pathspecs]
  (query :WITHOUT (concat [obj-or-sq] pathspecs)))

(defn merge
  "Merge objects (right-preferential)"
  [& objs]
  (query :MERGE objs))

;;; -- Sequence Ops --
(defn between
  "Get all elements of a sequence between two values"
  ([stream-selection x y]
     (query :BETWEEN [stream-selection x y]))
  ([stream-selection x y index]
     (query :BETWEEN [stream-selection x y] {:index index})))

(defn reduce
  ([sq lambda2]
     (query :REDUCE [sq lambda2]))
  ([sq lambda2 base]
     (query :REDUCE [sq lambda2] {:base base})))

(defn map
  [sq lambda1]
  (query :MAP [sq lambda1]))

(declare default)
;;; TODO - wat
(defn filter
  "Filter a sequence with either a function or a shortcut object.
The body of filter is wrapped in an implicit (default .. false) and you
can change the default value by specifying the default optarg. If you
make the default (error), all errors caught by default will be rethrown
as if the default did not exist"
  ([sq lambda1-or-obj]
     (-> (query :FILTER [sq lambda1-or-obj])
         (default false)))
  ([sq lambda1-or-obj default-val]
     (query :FILTER [sq lambda1-or-obj] {:default default-val})))

(defn mapcat
  "Map a function over a sequence and then concatenate the results together"
  [sq lambda1]
  (query :CONCATMAP [sq lambda1]))

;;; TODO
(defn order-by
  "Order a sequence based on one or more attributes"
  [sq wat]
  )

(defn distinct
  "Get all distinct elements of a sequence (like uniq)"
  [sq]
  (query :DISTINCT [sq]))

(defn count
  "Count the number of elements in a sequence, or only the elements that match a
given filter"
  ([sq]
     (query :COUNT [sq]))
  ([sq lambda1-or-x]
     (query :COUNT [sq lambda1-or-x])))

(defn empty?
  [sq]
  (query :IS_EMPTY [sq]))

(defn union
  "Take the union of multiple sequences
 (preserves duplicate elements (use distinct))"
  [& seqs]
  (query :UNION seqs))

(defn nth
  "Get the nth element of a sequence"
  [sq n]
  (query :NTH [sq n]))

(defn grouped-map-reduce
  "Takes a sequence and three functions:
- a function to group the sequence by
- a function to map over the groups
- a reduction to apply to each of the groups"
  ([sq lambda1 lambda1-2 lambda2]
     (query :GROUPED_MAP_REDUCE [sq lambda1 lambda1-2 lambda2]))
  ([sq lambda1 lambda1-2 lambda2 base]
     (query :GROUPED_MAP_REDUCE [sq lambda1 lambda1-2 lambda2] {:base base})))

;;; TODO
(defn group-by
  "Groups a sequence by one or more attributes and then applies a reduction.
The third argument is a special object literal giving the kind of operation
to be performed and anay necessary arguments.

At present group-by supports the following operations
- :count - count the size of the group
- {:sum attr} - sum the values of the given attribute accross the group
- {:avg attr} - average the values of the given attribute accross the group"
  [sq array operation]
  (let [operation (name operation)]
    (query :GROUPBY [sq array operation])))

(defn inner-join
  [sq1 sq2 lambda2]
  (query :INNER_JOIN [sq1 sq2 lambda2]))

(defn outer-join
  [sq1 sq2 lambda2]
  (query :OUTER_JOIN [sq1 sq2 lambda2]))

;;; TODO
(defn eq-join
  "An inner-join that does an equality comparison on two attributes"
  ([sq1 x sq2]
     (query :EQ_JOIN [sq1 x sq2]))
  ([sq1 x sq2 index]
     (query :EQ_JOIN [sq1 x sq2] {:index index})))

(defn zip
  [sq]
  (query :ZIP [sq]))

;;; -- Array Ops --
(defn insert-at
  "Insert an element in to an array at a given index"
  [array n x]
  (query :INSERT_AT [array n x]))

(defn delete-at
  "Remove an element at a given index from an array"
  ([array n]
     (query :DELETE_AT [array n]))
  ([array n1 n2]
     (query :DELETE_AT [array n1 n2])))

(defn change-at
  "Change the element at a given index of an array"
  [array n x]
  (query :CHANGE_AT [array n x]))

(defn splice-at
  "Splice one array in to another array"
  [array1 n array2]
  (query :SPLICE_AT [array1 n array2]))

;;; -- Type Ops --
;;; Figure out the name of types
(defn coerce-to
  "Coerces a datum to a named type (eg bool)"
  [x type]
  (let [type (name type)]
    (query :COERCE_TO [x type])))

(defn type
  "Returns the named type of a datum"
  [x]
  (query :TYPE_OF [x]))

;;; -- Write Ops --
(defn update
  "Updates all the rows in a selection.
Calls its function with the row to be updated and then merges the result of
that call

Optargs: :non-atomic -> bool
         :durability -> str
         :return-vals -> bool"
  [stream-or-single-selection lambda1-or-obj
   & {:as optargs}]
  (if-not optargs
    (query :UPDATE [stream-or-single-selection lambda1-or-obj])
    (let [optargs {}
          optargs (if (clojure.core/contains? :non-atomic)
                    (assoc optargs :non_atomic (:non-atomic optargs)))
          optargs (if (clojure.core/contains? :durability)
                    (assoc optargs :durability (:durability optargs)))
          optargs (if (clojure.core/contains? :return-vals)
                    (assoc optargs :return-vals (:return-vals optargs)))]
      (query :UPDATE [stream-or-single-selection lambda1-or-obj] optargs))))

(defn delete
  "Deletes all the rows in a selection

Optargs: :durability -> str
         :return-vals -> bool"
  [stream-or-single-selection & {:as optargs}]
  (if-not optargs
    (query :DELETE [stream-or-single-selection])
    (let [optargs {}
          optargs (if (clojure.core/contains? :durability)
                    (assoc optargs :durability (:durability optargs)))
          optargs (if (clojure.core/contains? :return-vals)
                    (assoc optargs :return-vals (:return-vals optargs)))]
      (query :DELETE [stream-or-single-selection] optargs))))

(defn replace
  "Replaces all the rows in a selection. Calls its function with the row to be
replaced, and then discards it and stores the result of that call

Optargs: :non-atomic -> bool
         :durability -> str
         :return-vals -> bool"
  [stream-or-single-selection lambda1 & {:as optargs}]
  (if-not optargs
    (query :REPLACE [stream-or-single-selection lambda1])
    (let [optargs {}
          optargs (if (clojure.core/contains? :non-atomic)
                    (assoc optargs :non_atomic (:non-atomic optargs)))
          optargs (if (clojure.core/contains? :durability)
                    (assoc optargs :durability (:durability optargs)))
          optargs (if (clojure.core/contains? :return-vals)
                    (assoc optargs :return-vals (:return-vals optargs)))]
      (query :REPLACE [stream-or-single-selection lambda1] optargs))))

(defn insert
  "Insert into a table. If upsert is true, overwrites entries with the
same primary key (otherwise errors)

Optargs: :upsert -> bool
         :durability -> str
         :return-vals -> bool"
  [table obj-or-sq & {:as optargs}]
  (if-not optargs
    (query :INSERT [table obj-or-sq])
    (let [optargs {}
          optargs (if (clojure.core/contains? :upsert)
                    (assoc optargs :upsert (:upsert optargs)))
          optargs (if (clojure.core/contains? :durability)
                    (assoc optargs :durability (:durability optargs)))
          optargs (if (clojure.core/contains? :return-vals)
                    (assoc optargs :return-vals (:return-vals optargs)))]
      (query :INSERT [table obj-or-sq] optargs))))

;;; -- Administrative Ops --
(defn db-create
  "Creates a database with a particular name"
  [dbname]
  (let [dbname (name dbname)]
    (query :DB_CREATE [dbname])))

(defn db-drop
  "Drops a database with a particular name"
  [dbname]
  (let [dbname (name dbname)]
    (query :DB_DROP [dbname])))

(defn db-list
  "Lists all the databases by name"
  []
  (query :DB_LIST))

(defn table-create
  "Creates a table with a particular name in the default database

Optargs: :datacenter str
         :primary-key str
         :cache-size number
         :durability str"
  [tname & {:as optargs}]
  (let [tname (name tname)]
    (if-not optargs
      (query :TABLE_CREATE [tname])
      (let [optargs {}
            optargs (if (clojure.core/contains? :datacenter)
                      (assoc optargs :datacenter (:datacenter optargs)))
            optargs (if (clojure.core/contains? :primary-key)
                      (assoc optargs :primary_key (:primary-key optargs)))
            optargs (if (clojure.core/contains? :cache-size)
                      (assoc optargs :cache-size (:cache-size optargs)))
            optargs (if (clojure.core/contains? :durability)
                      (assoc optargs :durability (:durability optargs)))]
        (query :TABLE_CREATE [tname] optargs)))))

(defn table-create-db
  "Creates a table with a particular name in a particular database

Optargs: :datacenter str
         :primary-key str
         :cache-size number
         :durability str"
  [db tname & {:as optargs}]
  (let [tname (name tname)]
    (if-not optargs
      (query :TABLE_CREATE [db tname])
      (let [optargs {}
            optargs (if (clojure.core/contains? :datacenter)
                      (assoc optargs :datacenter (:datacenter optargs)))
            optargs (if (clojure.core/contains? :primary-key)
                      (assoc optargs :primary_key (:primary-key optargs)))
            optargs (if (clojure.core/contains? :cache-size)
                      (assoc optargs :cache-size (:cache-size optargs)))
            optargs (if (clojure.core/contains? :durability)
                      (assoc optargs :durability (:durability optargs)))]
        (query :TABLE_CREATE [db tname] optargs)))))

(defn table-drop
  "Drops a table with a particular name from the default database"
  [tname]
  (let [tname (name tname)]
    (query :TABLE_DROP [tname])))

(defn table-drop-db
  "Drops a table with a particular name from a particular database"
  [db tname]
  (let [tname (name tname)]
    (query :TABLE_DROP [db tname])))

;;; -- Secondary indexes Ops --
(defn index-create
  "Creates a new secondary index with a particular name and definition
Optarg: multi -> bool"
  ([table idx-name lambda1]
     (let [idx-name (name idx-name)]
       (query :INDEX_CREATE [table idx-name lambda1])))
  ([table idx-name lambda1 multi]
     (let [idx-name (name idx-name)]
       (query :INDEX_CREATE [table idx-name lambda1] {:multi multi}))))

(defn index-drop
  "Drops a secondary index with a particular name from the specified table"
  [table idx-name]
  (query :INDEX_DROP [table idx-name]))

(defn index-list
  "Lists all secondary indexes on a particular table"
  [table]
  (query :INDEX_LIST [table]))

;;; -- Control Operators --
(defn funcall
  "Calls a function on data"
  [lambda-n & xs]
  (query :FUNCALL (concat [lambda-n] xs)))

(defn branch
  "An if statement"
  [bool then else]
  (query :BRANCH [bool then else]))

(defn any
  "A short circuiting or that returns a boolean"
  [& bools]
  (query :ANY bools))

(defn all
  "Returns true if all of its arguments are true (short-circuits)"
  [& bools]
  (query :EVERY bools))

(defn foreach
  "Calls its function with each entry in the sequence and executes the array of
terms that function returns"
  [sq lambda1]
  (query :FOREACH [sq lambda1]))
