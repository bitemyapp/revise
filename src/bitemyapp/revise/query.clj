(ns bitemyapp.revise.query
  "The clojure REQL API"
  (:refer-clojure :exclude [make-array get = not= < <= > >=
                            not + - * / mod contains? keys
                            merge reduce map filter mapcat
                            distinct count empty? nth
                            group-by type replace time])
  (:require [bitemyapp.revise.utils.case :refer [snake-case-keys
                                                 capitalize-map]]))

(defn datum?
  [m]
  (boolean
   (#{:R_STR :R_NUM :R_NULL :R_BOOL :R_ARRAY :R_OBJECT} (::type m))))

(declare parse-val make-obj query)

(defn parse-map
  "Decide if a map (not an optargs map) should be made with make-obj
 (it has terms inside) or is a simple datum (primitive type)"
  [m]
  (let [vs (vals m)
        vs (clojure.core/map parse-val vs)]
    (if (some (complement datum?) vs)
      (make-obj m)
      {::type :R_OBJECT
       ::value (clojure.core/mapv (fn [k v]
                       {:key (name k)
                        :val v})
                     ;; UUURRRRGH
                     (clojure.core/keys m) vs)})))

(defn parse-array
  "Decide if an array (not an args array) should be made with make-array
 (it has terms inside) or is a simple datum (primitive type)"
  [sq]
  (let [xs (clojure.core/mapv parse-val sq)]
    (if (some (complement datum?) xs)
      ;; Manual invocation of query
      {::type :MAKE_ARRAY
       ::args {::type :args
               ::value xs}}
      {::type :R_ARRAY
       ::value xs})))

(defn parse-val
  [x]
  (letfn [(dt-map [t val]
            (cond
             (clojure.core/= :R_STR t)
             {::type t
              ::value (name val)}
             (clojure.core/= :R_ARRAY t)
             (parse-array val)
             (clojure.core/= :R_OBJECT t)
             (parse-map val)
             :else
             {::type t
              ::value val}))]
    (if (and (clojure.core/map? x) (::type x))
      x
      (-> (cond (or (keyword? x) (string? x)) :R_STR
                (number? x) :R_NUM
                (nil? x) :R_NULL
                (vector? x) :R_ARRAY
                (and (clojure.core/map? x)
                     (clojure.core/not (::type x))) :R_OBJECT
                (or (false? x) (true? x)) :R_BOOL)
          (dt-map x)))))

;;; -------------------------------------------------------------------------
;;; General stuff

(defn query
  ([type]
     {::type type})
  ([type args]
     {::type type
      ::args {::type :args
              ::value (clojure.core/mapv parse-val args)}})
  ([type args optargs-map]
     (if (seq args)
       {::type type
        ::args {::type :args
                ::value (clojure.core/mapv parse-val args)}
        ::optargs {::type :optargs
                   ::value
                   (zipmap (clojure.core/map name (clojure.core/keys optargs-map))
                           (clojure.core/map parse-val (vals optargs-map)))}}
       {::type type
        ::optargs {::type :optargs
                   ::value
                   (zipmap (clojure.core/map name (clojure.core/keys optargs-map))
                           (clojure.core/map parse-val (vals optargs-map)))}})))

;;; -------------------------------------------------------------------------
;;; Lambdas

(defn index-args
  [lambda-args]
  (zipmap lambda-args
          (clojure.core/map (fn [n]
                 {::type :var
                  ::number (parse-val (inc n))})
               (range))))

(defmacro lambda
  [arglist & body]
  (let [ret (last body)
        arg-replacements (index-args arglist)]
    `(query :FUNC [(vec (clojure.core/map inc (range ~(clojure.core/count arglist))))
                   ;; TODO - not the best model of scope
                   ~(clojure.walk/postwalk-replace arg-replacements ret)])))

;;; -------------------------------------------------------------------------
;;; Terms

;;; -- Compound types --
(defn make-array
  [& xs]
  (query :MAKE_ARRAY xs))

(defn make-obj
  "Takes a map and returns an object. Useful for making maps with terms inside
such as the maps returned by lambdas passed as arguments to update."
  [m]
  (query :MAKE_OBJ nil m))

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
     (query :GET_ALL (concat [table] xs)))
  ([table xs index]
     (query :GET_ALL (concat [table] xs) {:index index})))

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

;; Weird stuff happens when we redefine / and use it from another namespace
(defn div
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
  ([stream-selection lower upper]
     (query :BETWEEN [stream-selection lower upper]))
  ([stream-selection lower upper index]
     (query :BETWEEN [stream-selection lower upper] {:index index})))

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
  [sq & strs-or-orderings]
  (query :ORDERBY (concat [sq] strs-or-orderings)))

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
  (let [operation-obj
        (-> (if (keyword? operation)
              {operation operation}
              operation)
            capitalize-map)]
    (query :GROUPBY [sq array operation-obj])))

(defn inner-join
  [sq1 sq2 lambda2]
  (query :INNER_JOIN [sq1 sq2 lambda2]))

(defn outer-join
  [sq1 sq2 lambda2]
  (query :OUTER_JOIN [sq1 sq2 lambda2]))

;;; TODO
(defn eq-join
  "An inner-join that does an equality comparison on two attributes"
  ([sq1 str sq2]
     (query :EQ_JOIN [sq1 str sq2]))
  ([sq1 str sq2 index]
     (query :EQ_JOIN [sq1 str sq2] {:index index})))

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
  (query :TYPEOF [x]))

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
    (query :UPDATE [stream-or-single-selection lambda1-or-obj]
           (snake-case-keys optargs))))

(defn delete
  "Deletes all the rows in a selection

Optargs: :durability -> str
         :return-vals -> bool"
  [stream-or-single-selection & {:as optargs}]
  (if-not optargs
    (query :DELETE [stream-or-single-selection])
    (query :DELETE [stream-or-single-selection]
           (snake-case-keys optargs))))

(defn replace
  "Replaces all the rows in a selection. Calls its function with the row to be
replaced, and then discards it and stores the result of that call

Optargs: :non-atomic -> bool
         :durability -> str
         :return-vals -> bool"
  [stream-or-single-selection lambda1 & {:as optargs}]
  (if-not optargs
    (query :REPLACE [stream-or-single-selection lambda1])
    (query :REPLACE [stream-or-single-selection lambda1]
           (snake-case-keys optargs))))

(defn insert
  "Insert into a table. If upsert is true, overwrites entries with the
same primary key (otherwise errors)

Optargs: :upsert -> bool
         :durability -> str
         :return-vals -> bool"
  [table obj-or-sq & {:as optargs}]
  (if-not optargs
    (query :INSERT [table obj-or-sq])
    (query :INSERT [table obj-or-sq]
           (snake-case-keys optargs))))

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
      (query :TABLE_CREATE [tname] (snake-case-keys optargs)))))

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
      (query :TABLE_CREATE [db tname]
             (snake-case-keys optargs)))))

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

(defn table-list
  "Lists all the tables in the default database"
  []
  (query :TABLE_LIST))

(defn table-list-db
  "Lists all the tables in a particular database"
  [db]
  (query :TABLE_LIST [db]))

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
  (query :ALL bools))

(defn foreach
  "Calls its function with each entry in the sequence and executes the array of
terms that function returns"
  [sq lambda1]
  (query :FOREACH [sq lambda1]))

;;; -- Special Ops --
(defn asc
  "Indicates to order-by that this attribute is to be sorted in ascending order"
  [k]
  (query :ASC [k]))

(defn desc
  "Indicates to order-by that this attribute is to be sorted in descending order"
  [k]
  (query :DESC [k]))

(defn info
  "Gets info about anything. INFO is most commonly called on tables"
  [x]
  (query :INFO [x]))

(defn match
  "(match a b) returns a match object if the string \"a\" matches the regexp #\"b\""
  [s re]
  (query :MATCH [s (str re)]))

(defn sample
  "Select a number of elements from sequence with uniform distribution"
  [sq n]
  (query :SAMPLE [sq n]))

(defn default
  "Evaluates its first argument. If that argument returns NULL or throws an error
related to the absence of an expected value, default will either return its
second argument or execute it if it's a function. If the second argument is a
function it will be passed either the text of the error or NULL as its argument"
  [to-check lambda1-or-x]
  (query :DEFAULT [to-check lambda1-or-x]))

(defn json
  "Parses its first argument as a json string and returns it as a datum"
  [s]
  (query :JSON [s]))

;;; -- Date/Time Ops --

(defn iso8601
  "Parses its first arguments as an ISO 8601 time and returns it as a datum"
  [s]
  (query :ISO8601 [s]))

(defn ->iso8601
  "Prints a time as an ISO 8601 time"
  [t]
  (query :TO_ISO8601 [t]))

(defn epoch-time
  "Returns a time given seconds since epoch in UTC"
  [n]
  (query :EPOCH_TIME [n]))

(defn ->epoch-time
  "Returns seconds since epoch in UTC given a time"
  [t]
  (query :TO_EPOCH_TIME [t]))

(defn now
  "The time the query was received by the server"
  []
  (query :NOW))

(defn in-timezone
  "Puts a time into an ISO 8601 timezone"
  [t s]
  (query :IN_TIMEZONE [t s]))

(defn during
  "(during a b c) returns whether a is in the range [b, c)
a b and c are times"
  [a b c]
  (query :DURING [a b c]))

(defn date
  "Retrieves the date portion of a time"
  [t]
  (query :DATE [t]))

(defn time-of-day
  "(time-of-day x) == (- (date x) x)"
  [t]
  (query :TIME_OF_DAY [t]))

(defn timezone
  [t]
  (query :TIMEZONE [t]))

;;; -- Accessing time components --
(defn year
  [t]
  (query :YEAR [t]))

(defn month
  [t]
  (query :MONTH [t]))

(defn day
  [t]
  (query :DAY [t]))

(defn day-of-week
  [t]
  (query :DAY_OF_WEEK [t]))

(defn day-of-year
  [t]
  (query :DAY_OF_YEAR [t]))

(defn hours
  [t]
  (query :HOURS [t]))

(defn minutes
  [t]
  (query :MINUTES [t]))

(defn seconds
  [t]
  (query :SECONDS [t]))

;;; -- Date construction --
;;; Apparently timezone is obligatory contrary to what the .proto docs say
(defn time
  "Construct a time from a date and optional timezone or a date+time and optional
timezone"
  ;; ([y m d]
  ;;    (query :TIME [y m d]))
  ([y m d tz]
     (query :TIME [y m d tz]))
  ;; ([y m d h min s]
  ;;    (query :TIME [y m d h min s]))
  ([y m d h min s tz]
     (query :TIME [y m d h min s tz])))

;;; -- Constants for ISO 8601 days of the week --
;;; Todo - Wait how?

;;; -- Bonus Term --
(defn literal
  "Indicates to MERGE to replace the other object rather than merge it"
  [json]
  (query :LITERAL [json]))
