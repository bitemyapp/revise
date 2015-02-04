(ns bitemyapp.revise.query
  "The clojure REQL API"
  (:refer-clojure :exclude [make-array get = not= < <= > >=
                            not + - * / mod contains? keys
                            merge reduce map filter mapcat
                            distinct count empty? nth
                            group-by type replace time
                            min max sync])
  (:require [bitemyapp.revise.utils.case :refer [snake-case-keys
                                                 uppercase-keys]]
            clojure.walk))

(defn datum?
  [m]
  (boolean
   (#{:R_STR :R_NUM :R_NULL :R_BOOL :R_ARRAY :R_OBJECT} (::type m))))

(declare parse-val make-obj)

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
      ;; Manual invocation of term
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
    (if (clojure.core/and (clojure.core/map? x) (::type x))
      x
      (-> (cond (clojure.core/or (keyword? x) (string? x)) :R_STR
                (number? x) :R_NUM
                (nil? x) :R_NULL
                (vector? x) :R_ARRAY
                (clojure.core/and (clojure.core/map? x)
                     (clojure.core/not (::type x))) :R_OBJECT
                (clojure.core/or (false? x) (true? x)) :R_BOOL)
          (dt-map x)))))

;;; -------------------------------------------------------------------------
;;; General stuff

(defn term
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
    `(term :FUNC [(vec (clojure.core/map inc (range ~(clojure.core/count arglist))))
                   ;; TODO - not the best model of scope
                   ~(clojure.walk/postwalk-replace arg-replacements ret)])))

;;; -------------------------------------------------------------------------
;;; Terms

;;; -- Compound types --
(defn make-array
  [& xs]
  (term :MAKE_ARRAY xs))

(defn make-obj
  "Takes a map and returns an object. Useful for making maps with terms inside
such as the maps returned by lambdas passed as arguments to update."
  [m]
  (term :MAKE_OBJ nil m))

(defn js
  ([s] (term :JAVASCRIPT [s]))
  ([s timeout] (term :JAVASCRIPT [s] {:timeout timeout})))

;; TODO - not yet available in 1.12
#_(defn http
  "Takes an HTTP URL and gets it. If the get succeeds and returns valid JSON, it
is converted into a DATUM.
Takes an optional map with any of the following keys:
data
method
params
header
attempts
redirects
verify
depaginate
auth
result_format"
  ([s] (term :HTTP [s]))
  ([s opts] (term :HTTP [s] opts)))

(defn error
  ([] (term :ERROR))
  ([s] (term :ERROR [s])))

(def implicit-var
  "Returns a reference to the implicit value"
  (term :IMPLICIT_VAR))

;;; -- Data Operators --
(defn db
  [db-name]
  (term :DB [db-name]))

(defn table-default
  ([table-name]
     (term :TABLE [table-name]))
  ([table-name use-outdated?]
     (term :TABLE [table-name] {:use_outdated use-outdated?})))

(defn table
  ([db table-name]
     (term :TABLE [db table-name]))
  ([db table-name use-outdated?]
     (term :TABLE [db table-name] {:use_outdated use-outdated?})))

(defn get
  [table k]
  (let [k (name k)]
    (term :GET [table k])))

(defn get-all
  ([table xs]
     (term :GET_ALL (concat [table] xs)))
  ([table xs index]
     (term :GET_ALL (concat [table] xs) {:index index})))

;;; -- DATUM Ops --
(defn =
  [& args]
  (term :EQ args))

(defn not=
  [& args]
  (term :NE args))

(defn <
  [& args]
  (term :LT args))

(defn <=
  [& args]
  (term :LE args))

(defn >
  [& args]
  (term :GT args))

(defn >=
  [& args]
  (term :GE args))

(defn not
  [bool]
  (term :NOT [bool]))

(defn +
  "Add two numbers or concatenate two strings"
  [& args]
  (term :ADD args))

(defn -
  [& args]
  (term :SUB args))

(defn *
  [& args]
  (term :MUL args))

;; Weird stuff happens when we redefine / and use it from another namespace
(defn div
  [& args]
  (term :DIV args))

(defn mod
  [n1 n2]
  (term :MOD [n1 n2]))

;;; -- Datum Array Ops --
(defn append
  [array x]
  (term :APPEND [array x]))

(defn prepend
  [array x]
  (term :PREPEND [array x]))

(defn difference
  [array1 array2]
  (term :DIFFERENCE [array1 array2]))

;;; -- Set Ops --
;;; No actual sets on rethinkdb, only arrays
(defn set-insert
  [array x]
  (term :SET_INSERT [array x]))

(defn set-intersection
  [array1 array2]
  (term :SET_INTERSECTION [array1 array2]))

(defn set-union
  [array1 array2]
  (term :SET_UNION [array1 array2]))

(defn set-difference
  [array1 array2]
  (term :SET_DIFFERENCE [array1 array2]))

(defn slice
  [sq n1 n2]
  (term :SLICE [sq n1 n2]))

(defn skip
  [sq n]
  (term :SKIP [sq n]))

(defn limit
  [sq n]
  (term :LIMIT [sq n]))

(defn indexes-of
  [sq lambda1-or-x]
  (term :INDEXES_OF [sq lambda1-or-x]))

(defn contains?
  [sq lambda1-or-x]
  (term :CONTAINS [sq lambda1-or-x]))

;;; -- Stream/Object Ops --
(defn get-field
  "Get a particular field from an object or map that over a sequence"
  [obj-or-sq s]
  (term :GET_FIELD [obj-or-sq s]))

(defn object
  "Creates a javascript object from k/v pairs - consider simply using a clojure
map. Usage similar to clojure.core/hash-map"
  [& key-vals]
  (term :OBJECT key-vals))

(defn keys
  "Return an array containing the keys of the object"
  [obj]
  (term :KEYS [obj]))

(defn has-fields?
  "Check whether an object contains all the specified fields or filters a
sequence so that al objects inside of it contain all the specified fields"
  [obj & pathspecs]
  (term :HAS_FIELDS (concat [obj] pathspecs)))

(defn with-fields
  "(with-fields sq pathspecs..) <=> (pluck (has-fields sq pathspecs..) pathspecs..)"
  [sq & pathspecs]
  (term :HAS_FIELDS (concat [sq] pathspecs)))

(defn pluck
  "Get a subset of an object by selecting some attributes to preserve,
or map that over a sequence"
  [obj-or-sq & pathspecs]
  (term :PLUCK (concat [obj-or-sq] pathspecs)))

(defn without
  "Get a subset of an object by selecting some attributes to discard,
or map that over a sequence"
  [obj-or-sq & pathspecs]
  (term :WITHOUT (concat [obj-or-sq] pathspecs)))

(defn merge
  "Merge objects (right-preferential)"
  [& objs]
  (term :MERGE objs))

(defn literal
  "Indicates to MERGE to replace the other object rather than merge it"
  [json]
  (term :LITERAL [json]))

;;; -- Sequence Ops --
(defn group
  "Takes a stream and partitions it into multiple groups based on the fields or
functions provided. Commands chained after group will be called on each of these
grouped sub-streams, producing grouped data.

Examples:
;; group by a key
(group tbl [:player])
;; group by a lambda
(group tbl [(lambda [game] (pluck game :player :type))])
;; group by an index
(group tbl [] :index :type)"
  [sq ks-or-lambda1s & {:keys [index]}]
  (if index
    (term :GROUP (concat [sq] ks-or-lambda1s) {:index index})
    (term :GROUP (concat [sq] ks-or-lambda1s))))

(defn sum
  "Sums all the elements of a sequence. If called with a field name, sums all
the values of that field in the sequence, skipping elements of the sequence that
lack that field. If called with a function, calls that function on every element
of the sequence and sums the results, skipping elements of the sequence where
that function returns nil or a non-existence error."
  ([sq & [k-or-lambda1]]
     (if k-or-lambda1
       (term :SUM [sq k-or-lambda1])
       (term :SUM [sq]))))

(defn avg
  "Averages all the elements of a sequence. If called with a field name,
averages all the values of that field in the sequence, skipping elements of the
sequence that lack that field. If called with a function, calls that function on
every element of the sequence and averages the results, skipping elements of the
sequence where that function returns nil or a non-existence error."
  ([sq & [k-or-lambda1]]
     (if k-or-lambda1
       (term :AVG [sq k-or-lambda1])
       (term :AVG [sq]))))

(defn min
  "Finds the minimum of a sequence. If called with a field name, finds the
element of that sequence with the smallest value in that field. If called with a
function, calls that function on every element of the sequence and returns the
element which produced the smallest value, ignoring any elements where the
function returns nil or produces a non-existence error."
  ([sq & [k-or-lambda1]]
     (if k-or-lambda1
       (term :MIN [sq k-or-lambda1])
       (term :MIN [sq]))))

(defn max
  "Finds the maximum of a sequence. If called with a field name, finds the
element of that sequence with the largest value in that field. If called with a
function, calls that function on every element of the sequence and returns the
element which produced the largest value, ignoring any elements where the
function returns nil or produces a non-existence error."
  ([sq & [k-or-lambda1]]
     (if k-or-lambda1
       (term :MAX [sq k-or-lambda1])
       (term :MAX [sq]))))

(defn between
  "Get all elements of a sequence between two values"
  ([stream-selection lower upper]
     (term :BETWEEN [stream-selection lower upper]))
  ([stream-selection lower upper index]
     (term :BETWEEN [stream-selection lower upper] {:index index})))

(defn reduce
  ([sq lambda2]
     (term :REDUCE [sq lambda2]))
  ([sq lambda2 base]
     (term :REDUCE [sq lambda2] {:base base})))

(defn map
  [sq lambda1]
  (term :MAP [sq lambda1]))

(defn filter
  "Filter a sequence with either a function or a shortcut object.
The body of filter is wrapped in an implicit (default .. false) and you
can change the default value by specifying the default optarg. If you
make the default (error), all errors caught by default will be rethrown
as if the default did not exist"
  ([sq lambda1-or-obj]
     (term :FILTER [sq lambda1-or-obj]))
  ([sq lambda1-or-obj default-val]
     (term :FILTER [sq lambda1-or-obj] {:default default-val})))

(defn mapcat
  "Map a function over a sequence and then concatenate the results together"
  [sq lambda1]
  (term :CONCATMAP [sq lambda1]))

(defn order-by
  "Order a sequence based on one or more attributes"
  [sq & strs-or-orderings]
  (term :ORDERBY (concat [sq] strs-or-orderings)))

(defn distinct
  "Get all distinct elements of a sequence (like uniq)"
  [sq]
  (term :DISTINCT [sq]))

(defn count
  "Count the number of elements in a sequence, or only the elements that match a
given filter"
  ([sq]
     (term :COUNT [sq]))
  ([sq lambda1-or-x]
     (term :COUNT [sq lambda1-or-x])))

(defn empty?
  [sq]
  (term :IS_EMPTY [sq]))

(defn union
  "Take the union of multiple sequences
 (preserves duplicate elements (use distinct))"
  [& seqs]
  (term :UNION seqs))

(defn nth
  "Get the nth element of a sequence"
  [sq n]
  (term :NTH [sq n]))

;; Removed in version 1.12 of rethinkdb, use group, map and reduce instead
#_(defn grouped-map-reduce
  "Takes a sequence and three functions:
- a function to group the sequence by
- a function to map over the groups
- a reduction to apply to each of the groups"
  ([sq lambda1 lambda1-2 lambda2]
     (term :GROUPED_MAP_REDUCE [sq lambda1 lambda1-2 lambda2]))
  ([sq lambda1 lambda1-2 lambda2 base]
     (term :GROUPED_MAP_REDUCE [sq lambda1 lambda1-2 lambda2] {:base base})))

;; Removed in version 1.12 of rethinkdb, use group instead
#_(defn group-by
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
            uppercase-keys)]
    (term :GROUPBY [sq array operation-obj])))

(defn inner-join
  [sq1 sq2 lambda2]
  (term :INNER_JOIN [sq1 sq2 lambda2]))

(defn outer-join
  [sq1 sq2 lambda2]
  (term :OUTER_JOIN [sq1 sq2 lambda2]))

(defn eq-join
  "An inner-join that does an equality comparison on two attributes"
  ([sq1 str sq2]
     (term :EQ_JOIN [sq1 str sq2]))
  ([sq1 str sq2 index]
     (term :EQ_JOIN [sq1 str sq2] {:index index})))

(defn zip
  [sq]
  (term :ZIP [sq]))

;;; -- Array Ops --
(defn insert-at
  "Insert an element in to an array at a given index"
  [array n x]
  (term :INSERT_AT [array n x]))

(defn delete-at
  "Remove an element at a given index from an array"
  ([array n]
     (term :DELETE_AT [array n]))
  ([array n1 n2]
     (term :DELETE_AT [array n1 n2])))

(defn change-at
  "Change the element at a given index of an array"
  [array n x]
  (term :CHANGE_AT [array n x]))

(defn splice-at
  "Splice one array in to another array"
  [array1 n array2]
  (term :SPLICE_AT [array1 n array2]))

;;; -- Type Ops --
;;; Figure out the name of types
(defn coerce-to
  "Coerces a datum to a named type (eg bool)"
  [x type]
  (let [type (name type)]
    (term :COERCE_TO [x type])))

(defn type
  "Returns the named type of a datum"
  [x]
  (term :TYPEOF [x]))

;;; -- Write Ops --
(defn update
  "Updates all the rows in a selection.
Calls its function with the row to be updated and then merges the result of
that call

Optargs: :non-atomic -> bool - Allow the server to run non-atomic operations
         :durability -> :soft or :hard - Override the table durability for this
operation
         :return-vals -> bool - Only valid for single-row modifications. If true
return the new value in :new_val and the old one in old_val"
  [stream-or-single-selection lambda1-or-obj
   & {:as optargs}]
  (if-not optargs
    (term :UPDATE [stream-or-single-selection lambda1-or-obj])
    (term :UPDATE [stream-or-single-selection lambda1-or-obj]
           (snake-case-keys optargs))))

(defn delete
  "Deletes all the rows in a selection

Optargs: :durability -> :hard or :soft - Override the table or query's default
                                         durability setting.
         :return-vals -> bool - Only valid for single row deletions. If true
                                get the value you deleted in :old_val"
  [stream-or-single-selection & {:as optargs}]
  (if-not optargs
    (term :DELETE [stream-or-single-selection])
    (term :DELETE [stream-or-single-selection]
           (snake-case-keys optargs))))

(defn replace
  "Replaces all the rows in a selection. Calls its function with the row to be
replaced, and then discards it and stores the result of that call

Optargs: :non-atomic -> bool
         :durability -> str
         :return-vals -> bool"
  [stream-or-single-selection lambda1 & {:as optargs}]
  (if-not optargs
    (term :REPLACE [stream-or-single-selection lambda1])
    (term :REPLACE [stream-or-single-selection lambda1]
           (snake-case-keys optargs))))

(defn insert
  "Insert into a table. If upsert is true, overwrites entries with the
same primary key (otherwise errors)

Optargs: :upsert -> bool - If true -> overwrite the data if it already exists
         :durability -> :soft or :hard -> Overrule the durability with which the
                                          table was created
         :return-vals -> bool - Only valid for single object inserts. If true
                                get back the row you inserted on the key :nev_val"
  [table obj-or-sq & {:as optargs}]
  (if-not optargs
    (term :INSERT [table obj-or-sq])
    (term :INSERT [table obj-or-sq]
           (snake-case-keys optargs))))

;;; -- Administrative Ops --
(defn db-create
  "Creates a database with a particular name"
  [dbname]
  (let [dbname (name dbname)]
    (term :DB_CREATE [dbname])))

(defn db-drop
  "Drops a database with a particular name"
  [dbname]
  (let [dbname (name dbname)]
    (term :DB_DROP [dbname])))

(defn db-list
  "Lists all the databases by name"
  []
  (term :DB_LIST))

(defn table-create-default
  "Creates a table with a particular name in the default database

Optargs: :datacenter str
         :primary-key str
         :cache-size number
         :durability str"
  [tname & {:as optargs}]
  (let [tname (name tname)]
    (if-not optargs
      (term :TABLE_CREATE [tname])
      (term :TABLE_CREATE [tname] (snake-case-keys optargs)))))

(defn table-create
  "Creates a table with a particular name in a particular database

Optargs: :datacenter str
         :primary-key str
         :cache-size number
         :durability str"
  [db tname & {:as optargs}]
  (let [tname (name tname)]
    (if-not optargs
      (term :TABLE_CREATE [db tname])
      (term :TABLE_CREATE [db tname]
             (snake-case-keys optargs)))))

(defn table-drop-default
  "Drops a table with a particular name from the default database"
  [tname]
  (let [tname (name tname)]
    (term :TABLE_DROP [tname])))

(defn table-drop
  "Drops a table with a particular name from a particular database"
  [db tname]
  (let [tname (name tname)]
    (term :TABLE_DROP [db tname])))

(defn table-list-db
  "Lists all the tables in the default database"
  []
  (term :TABLE_LIST))

(defn table-list
  "Lists all the tables in a particular database"
  [db]
  (term :TABLE_LIST [db]))

(defn sync
  "Ensures that previously issued soft-durability writes are complete and
written to disk"
  [table]
  (term :SYNC [table]))

;;; -- Secondary indexes Ops --
(defn index-create
  "Creates a new secondary index with a particular name and definition
Optarg: multi -> bool"
  ([table idx-name lambda1]
     (let [idx-name (name idx-name)]
       (term :INDEX_CREATE [table idx-name lambda1])))
  ([table idx-name lambda1 multi]
     (let [idx-name (name idx-name)]
       (term :INDEX_CREATE [table idx-name lambda1] {:multi multi}))))

(defn index-drop
  "Drops a secondary index with a particular name from the specified table"
  [table idx-name]
  (term :INDEX_DROP [table idx-name]))

(defn index-list
  "Lists all secondary indexes on a particular table"
  [table]
  (term :INDEX_LIST [table]))

(defn index-status
  "Gets information about whether or not a set of indexes are ready to be
accessed. Returns a list of objects (clojure maps) that look like this:
 {\"index\" string
  \"ready\" boolean
  \"blocks_processed\" number
  \"blocks-total\" number}"
  [table & idx-names]
  (term :INDEX_STATUS (concat [table] idx-names)))

(defn index-wait
  "Blocks until a set of indexes are ready to be accessed. Returns the same
values as index-status; a list of objects (clojure maps) that look like:
 {\"index\" string
  \"ready\" boolean
  \"blocks_processed\" number
  \"blocks-total\" number}"
  [table & idx-names]
  (term :INDEX_WAIT (concat [table] idx-names)))

;;; -- Control Operators --
(defn funcall
  "Calls a function on data"
  [lambda-n & xs]
  (term :FUNCALL (concat [lambda-n] xs)))

(defn branch
  "An if statement"
  [bool then else]
  (term :BRANCH [bool then else]))

(defn any
  "A short circuiting or that returns a boolean"
  [& bools]
  (term :ANY bools))

(defn all
  "Returns true if all of its arguments are true (short-circuits)"
  [& bools]
  (term :ALL bools))

(defn foreach
  "Calls its function with each entry in the sequence and executes the array of
terms that function returns"
  [sq lambda1]
  (term :FOREACH [sq lambda1]))

;;; -- Special Ops --
(defn asc
  "Indicates to order-by that this attribute is to be sorted in ascending order"
  [k]
  (term :ASC [k]))

(defn desc
  "Indicates to order-by that this attribute is to be sorted in descending order"
  [k]
  (term :DESC [k]))

(defn info
  "Gets info about anything. INFO is most commonly called on tables"
  [x]
  (term :INFO [x]))

(defn match
  "(match a b) returns a match object if the string \"a\" matches the regexp #\"b\""
  [s re]
  (term :MATCH [s (str re)]))

(defn upcase
  "Change a string to uppercase"
  [s]
  (term :UPCASE [s]))

(defn downcase
  "Change a string to downcase"
  [s]
  (term :DOWNCASE [s]))

(defn sample
  "Select a number of elements from sequence with uniform distribution"
  [sq n]
  (term :SAMPLE [sq n]))

(defn default
  "Evaluates its first argument. If that argument returns NULL or throws an error
related to the absence of an expected value, default will either return its
second argument or execute it if it's a function. If the second argument is a
function it will be passed either the text of the error or NULL as its argument"
  [to-check lambda1-or-x]
  (term :DEFAULT [to-check lambda1-or-x]))

(defn json
  "Parses its first argument as a json string and returns it as a datum"
  [s]
  (term :JSON [s]))

;;; -- Date/Time Ops --

(defn iso8601
  "Parses its first arguments as an ISO 8601 time and returns it as a datum"
  [s]
  (term :ISO8601 [s]))

(defn ->iso8601
  "Prints a time as an ISO 8601 time"
  [t]
  (term :TO_ISO8601 [t]))

(defn epoch-time
  "Returns a time given seconds since epoch in UTC"
  [n]
  (term :EPOCH_TIME [n]))

(defn ->epoch-time
  "Returns seconds since epoch in UTC given a time"
  [t]
  (term :TO_EPOCH_TIME [t]))

(defn now
  "The time the query was received by the server"
  []
  (term :NOW))

(defn in-timezone
  "Puts a time into an ISO 8601 timezone"
  [t s]
  (term :IN_TIMEZONE [t s]))

(defn during
  "(during a b c) returns whether a is in the range [b, c)
a b and c are times"
  [a b c]
  (term :DURING [a b c]))

(defn date
  "Retrieves the date portion of a time"
  [t]
  (term :DATE [t]))

(defn time-of-day
  "(time-of-day x) == (- (date x) x)"
  [t]
  (term :TIME_OF_DAY [t]))

(defn timezone
  [t]
  (term :TIMEZONE [t]))

;;; -- Accessing time components --
(defn year
  [t]
  (term :YEAR [t]))

(defn month
  [t]
  (term :MONTH [t]))

(defn day
  [t]
  (term :DAY [t]))

(defn day-of-week
  [t]
  (term :DAY_OF_WEEK [t]))

(defn day-of-year
  [t]
  (term :DAY_OF_YEAR [t]))

(defn hours
  [t]
  (term :HOURS [t]))

(defn minutes
  [t]
  (term :MINUTES [t]))

(defn seconds
  [t]
  (term :SECONDS [t]))

;;; -- Date construction --
;;; Apparently timezone is obligatory contrary to what the .proto docs say
(defn time
  "Construct a time from a date and optional timezone or a date+time and optional
timezone"
  ([y m d]
     (term :TIME [y m d "+00:00"]))
  ([y m d tz]
     (term :TIME [y m d tz]))
  ([y m d h min s]
     (term :TIME [y m d h min s "+00:00"]))
  ([y m d h min s tz]
     (term :TIME [y m d h min s tz])))

;;; -- Constants for ISO 8601 days of the week --
(def monday    (term :MONDAY))
(def tuesday   (term :TUESDAY))
(def wednesday (term :WEDNESDAY))
(def thursday  (term :THURSDAY))
(def friday    (term :FRIDAY))
(def saturday  (term :SATURDAY))
(def sunday    (term :SUNDAY))

;;; -- Constants for ISO 8601 months --
(def january   (term :JANUARY))
(def february  (term :FEBRUARY))
(def march     (term :MARCH))
(def april     (term :APRIL))
(def may       (term :MAY))
(def june      (term :JUNE))
(def july      (term :JULY))
(def august    (term :AUGUST))
(def september (term :SEPTEMBER))
(def october   (term :OCTOBER))
(def november  (term :NOVEMBER))
(def december  (term :DECEMBER))

;;; -------------------------------------------------------------------------
;;; Extra stuff

(defn split
  "Returns an array of an split string
(split s) splits on whitespace
(split s \" \") splits on spaces only
(split s \" \" 5) splits on spaces with at most 5 results
(split s nil 5) splits on whitespace with at most 5 results"
  ([s] (term :SPLIT [s]))
  ([s splitter] (term :SPLIT [s splitter]))
  ([s splitter result-count] (term :SPLIT [s splitter result-count])))

(defn ungroup
  [grouped-data]
  (term :UNGROUP [grouped-data]))

;; TODO - not yet available in 1.12
#_(defn random
  "Takes a range of numbers and returns a random number within the range"
  [from to & [float?]]
  (let [float? (boolean float?)])
  (term :RANDOM [from to] {:float float?}))

;; TODO - not yet available in 1.12
#_(defn changes
  [table]
  (term :CHANGES [table]))

;;; -------------------------------------------------------------------------
;;; Custom Helpers
