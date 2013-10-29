# Revise

Clojure RethinkDB client. Under development.

## Usage

Don't. Seriously. You'll know it if you're supposed to be here.

## Introduction

These docs are - for now - loosely based on the python api docs

## Connecting to rethinkdb

TODO

## Compiling and sending a query

Queries are compiled and sent to a connection with the fn `bitemyapp.revise.core/run`.

## API

The api is under the namespace bitemyapp.revise.query

```clojure
(require '[bitemyapp.revise.query :as r])
```

### Manipulating databases

#### db-create
`([db-name])`

Create a database.

```clojure
(r/db-create "my-db")
```

#### db-drop
`([db-name])`

Drop a database.

```clojure
(r/db-drop "my-db")
```

#### db-list
`([])`

List the database names in the system.

```clojure
(r/db-list)
```

### Manipulating tables

#### table-create-db

`([db table-name & {:as optargs}])`

Create a table on the specified database. The following options are available:

* `:primary-key` The name of the primary key. Default: `id`.
* `:durability` If set to `:soft`, this enables soft durability on this table:
writes will be acknowledged by the server immediately and flushed to disk in the
background. Default is `:hard` (acknowledgement of writes happens after data has been
written to disk).
* `:cache-size` Set the cache size (in bytes) to be used by the table.
The default is 1073741824 (1024MB).
* `:datacenter` The name of the datacenter this table should be assigned to.

```clojure
(-> (r/db "test") (r/table-create-db "authors"))
```

#### table-create

`([table-name & {:as optargs}])`

Like `table-create-db` except that the db is the default db.

```clojure
(r/table-create "authors")
```

#### table-drop-db

`([db table-name])`

Drop a table from a specific db. The table and all its data will be deleted.

```clojure
(-> (r/db "test") (r/table-drop-db "authors"))
```

#### table-drop

`([table-name])`

Like `table-drop-db` except the default db is used.

```clojure
(r/table-drop "authors")
```

#### index-create

`([table index-name lambda1 & [multi?]])`

Create a new secondary index with a given name on the specified table.

```clojure
(-> (r/table "authors")
  (r/index-create :author
                  (r/lambda [author]
                    (r/get-field author :name))))
;; Compound index
(-> (r/table "authors")
  (r/index-create :name-tv-show
                  (r/lambda [author]
                    [(r/get-field author :name)
                     (r/get-field author :tv-show)])))
;; A multi index. The r/lambda of a multi index should return an array. It will allow
;; you to query based on whether a value is present in the returned array
(-> (r/table "authors")
  (r/index-create :posts
                  (r/lambda [author]
                    (r/get-field author :posts)) ; returns an array
    true)) ; :multi -> true
```

#### index-drop

`([table index-name])`

Delete a previously created secondary index of this table.

```clojure
(-> (r/table "authors") (r/index-drop :posts))
```

#### index-list

`([table])`

List all the secondary indexes of this table.

```clojure
(-> (r/table "authors") (r/index-list))
```

### Writing data
#### insert

`([table data])`

Insert json documents into a table. Accepts a single json document (a clojure map) or
an array of documents (a clojure vector of clojure maps).

```clojure
(def authors [{:name "William Adama" :tv-show "Battlestar Galactica"
               :posts [{:title "Decommissioning speech",
                        :rating 3.5
                        :content "The Cylon War is long over..."},
                       {:title "We are at war",
                        :content "Moments ago, this ship received word..."},
                       {:title "The new Earth",
                        :content "The discoveries of the past few days..."}]}

              {:name "Laura Roslin", :tv-show "Battlestar Galactica",
               :posts [{:title "The oath of office",
                        :rating 4
                        :content "I, Laura Roslin, ..."},
                       {:title "They look like us",
                        :content "The Cylons have the ability..."}]}

              {:name "Jean-Luc Picard", :tv-show "Star Trek TNG",
               :posts [{:title "Civil rights",
                        :content "There are some words I've known since..."}]}])

(def jean-luc {:name "Jean-Luc Picard", :tv-show "Star Trek TNG",
               :posts [{:title "Civil rights",
                        :content "There are some words I've known since..."}]})

(-> (r/table "authors")
  (r/insert authors))

(-> (r/table "authors")
  (r/insert jean-luc))
```

Insert returns a map with the following attributes:

* `:inserted` The number of documents that were succesfully inserted.
* `:replaced` The number of documents that were updated when upsert is used.
* `:unchanged` The number of documents that would have been modified, except that
the new value was the same as the old value when doing an upsert.
* `:errors` The number of errors encountered while inserting; if errors were
encountered while inserting, first_error contains the text of the first error.
* `:generated_keys` A list of generated primary key values deleted and skipped:
0 for an insert operation.

#### update

`([stream-or-single-selection lambda1-or-obj])`

Update JSON documents in a table. Accepts a JSON document (clojure map), a RQL
expression or a combination of the two. Accepts the following optional keys:

* `:durability` Default: `:soft`
* `:return-vals` Default: `true`

```clojure
;; Make all authors be fictional
(-> (r/table "authors") (r/update {:type "fictional"}))
;; Add the rank of admiral to William Adama
(-> (r/table "authors")
  (r/filter (r/lambda [row]
              (r/= "William Adama"
                (r/get-field row :name))))
  (r/update {:rank "Admiral"}))
;; Add a post to Jean-Luc
(-> (r/table "authors")
  (r/filter (r/lambda [row]
              (r/= "Jean-Luc Picard"
                (r/get-field row :name))))
  (r/update
    (r/lambda [row]
      {:posts
       (r/append (r/get-field row :posts)
         {:title "Shakespeare"
          :content "What a piece of work is man.."})})))
```

Update returns a map that contains the following attributes:

* `:replaced` The number of documents that were updated.
* `:unchanged` The number of documents that would have been modified except the new
value was the same as the old value.
* `:skipped` The number of documents that were left unmodified because there was
nothing
to do: either the row didn't exist or the new value is null.
* `:errors` The number of errors encountered while performing the update; if errors
occured, first_error contains the text of the first error.
* `:deleted` and inserted: 0 for an update operation.

#### replace

`([stream-or-single-selection lambda1 & {:as optargs}])`

Replace documents in a table. The new document must have the same primary key as the
original document. Accepts the following optional arguments:

* `:non-atomic` allow non-atomic updates.
* `:durability` Default: `:soft`; Override the table or query's default durability
setting.
* `:return-vals` Default: `true`; Return the old and new values of the row you're
modifying when set to true (only valid for single row replacements).

```clojure
(-> (r/table "authors") (r/get "7644aaf2-9928-4231-aa68-4e65e31bf219")
  (r/replace {:tv-show "Jeeves"}))
```

#### delete

`([stream-or-single-selection & {:as optargs}])`

Delete the rows in a selection. Accepts the following optional arguments:

* `:durability` Default: `:soft`; Override the table or query's default durability
setting
* `:return-vals` Default: `true`; Return the old value of the row you're deleting when
set to true (only valid for single row deletes)

```clojure
(-> (r/table "authors")
  (r/filter (r/lambda [row]
              (r/< (r/count (r/get-field row :posts))
                   3)))
  (r/delete)
```

`delete` returns a map with the following attributes:

* `:deleted` The number of documents that were deleted.
* `:skipped` The number of documents from the selection that were left unmodified
because there was nothing to do. For example, if you delete a row that has already
been deleted, that row will be skipped.
* `:errors` The number of errors encountered while deleting if errors occured,
first_error contains the text of the first error.
* `:inserted` Replaced, and unchanged: all 0 for a delete operation.

### Selecting data

#### db

`([db-name])`

Reference a database

```clojure
(r/db "test")
(r/db :test)
```

#### table-db

`([db table-name])`

Select all documents on a table. This command can be chained with other commands to
do further processing on the data

```clojure
(-> (r/db "test") (r/table "authors"))
```
#### table

`([table-name])`

Like table-db except that it uses the default database.

```clojure
(r/table "authors")
```

#### get

`([table key])`

Get a document by its primary key.

```clojure
;; After setting the secondary index :name on the table "authors"
(-> (r/table "authors") (r/get "7644aaf2-9928-4231-aa68-4e65e31bf219"))
```

#### get-all

`([table keys-seq & [index]])`

Get all documents where the given value matches the value of the requested index

```clojure
;; After setting the secondary key :name on the table :authors
(-> (r/table "authors")
  (r/get-all "William Adama" :name))
```

#### between

`([stream-selection lower-key upper-key & [index]])`

Get all documents between two keys. index can be the name of a secondary index.
`[lower-key upper-key)`

```clojure
;; Assuming the primary key on our table is a number.
(-> (r/table "authors") (r/between 10 20))
```

#### filter

`([sequence lambda1-or-obj & [default-val]])`

Filter a sequence with either a function or a shortcut object.
The body of `filter` is wrapped in an implicit `(default .. false)`  and you
can change the default value by specifying the `default-val` optarg. If you
make the default `(error)`, all errors caught by default will be rethrown
as if the default did not exist

```clojure
(-> (r/table "authors")
    (r/filter (r/lambda [row]
                (r/= (r/get-field row :name) "William Adama"))))
```

### Joins

#### inner-join

`([sequence1 sequence2 predicate])`

Returns the inner product of two sequences (e.g. a table and a filter result) filtered
by the predicate. The query compares each row of the left sequence with each row of
the right sequence to find all pairs of rows which satisfy the predicate (a `lambda`
of two arguments). When the predicate is satisfied, each matched pair of rows of both
sequences are combined into a result row.

```clojure
(-> (r/table "marvel")
    (r/inner-join (r/table "dc")
      (lambda [marvel-row dc-row]
        (r/< (get-field marvel-row :strength)
             (get-field dc-row :strength)))))
```

#### outer-join

`([sequence1 sequence2 predicate])`

Computes a left outer join by retaining each row in the left table even if no match
was found in the right table.

```clojure
(-> (r/table "marvel")
    (r/outer-join (r/table "dc")
      (r/lambda [marvel-row dc-row]
        (r/< (get-field marvel-row :strength)
             (get-field dc-row :strength)))))
```

#### eq-join

`([sequence1 left-attr sequence2 & [index]])`

An efficient join that looks up elements in the right table by primary key.
`index` defaults to `:id`

```clojure
(-> (r/table "marvel") (r/eq-join "main_dc_collaborator" (r/table "dc")))
```

#### zip

`([sequence])`

Used to 'zip' up the result of a join by merging the 'right' fields into 'left' fields
of each member of the sequence.

```clojure
(-> (r/table "marvel") (r/eq-join "main_dc_collaborator" (r/table "dc"))
    (r/zip))
```

### Transformations

#### map

`([sequence lambda1])`

Transform each element of the sequence by applying the given mapping function.

```clojure
(-> (r/table "authors")
    (r/map (r/lambda [author]
             (r/count (r/get-field author :posts)))))
```

#### with-fields

`([sequence & pathspecs])`

Takes a sequence of objects and a variable number of fields. If any objects in the
sequence don't have all of the specified fields, they're dropped from the sequence.
The remaining objects have the specified fields plucked out. Identical to has-fields
followed by pluck.

```clojure
;; Get a list of authors and their posts, excluding any authors that lack one.
(-> (r/table "authors") (r/with-fields :name :posts))
```

#### mapcat

`([sequence lambda1])`

Map a function over a sequence and then concatenate the results together

```clojure
;; Get all of the posts of all authors
(-> (r/table "authors")
    (r/mapcat (r/lambda [author]
                (r/get-field author :posts))))
```

#### order-by

`([sequence & keys-or-orderings])`

Sort the sequence by document values of the given key(s). Defaults to ascending
ordering. To specify order, wrap the key with `(r/asc ..)` or `(r/desc ..)`

```clojure
(-> (r/table "marvel")
    (r/order-by :enemies_vanquished :damsels_saved))
```

#### skip

`([sequence n])`

Skip a number of elements from the head of the sequence

```clojure
;; Ignore the first authors sorted alphabetically
(-> (r/table "authors")
    (r/order-by :name)
    (r/skip 2))
```

#### limit

`([sequence n])`

End the sequence after the given number of elements

```clojure
;; Get 10 posts from all of our authors
(-> (r/table "authors")
    (r/mapcat (r/lambda [author]
                (r/get-field author :posts)))
    (r/limit 10))
```

#### slice

`([sequence start-index end-index])`

Trim the sequence to within the bounds provided.

```clojure
(-> (r/table "marvel")
    (r/order-by :strength)
    (r/slice 5 10))
```

#### nth

`([sequence idx])`

Get the nth element of a sequence. Zero indexed.

```clojure
(-> (r/table "authors")
    (r/nth 1))
```

#### indexes-of

`([sequence item-or-predicate])`

Get the indexes of an element in a sequence. If the argument is a predicate, get the
indexes of all elements matching it.

```clojure
;; Todo - needs an expr fn?
(-> (r/expr ["a" "b" "c"])
    (r/indexes-of "c"))
```

#### empty?

`([sequence])`

Test if a sequence is empty.

```clojure
(-> (r/table "authors")
    (r/empty?))
```

#### union

`([sequence1 sequence2])`

Concatenate 2 sequences

```clojure
(-> (r/table "marvel")
    (r/union
      (r/table "dc")))
```

#### sample

`([sequence n])`

Select a number of elements from the sequence with uniform random distribution.

```clojure
(-> (r/table "authors")
    (r/sample 2))
```

### Aggregation

Compute smaller values from large sequences.

#### reduce

`([sequence lambda2 & [init-val]])`

Produce a single value from a sequence through repeated application of a reduction
function.

```clojure
;; How many posts are there?
(-> (r/table "authors")
    (r/map (r/lambda [author] (r/count (r/get-field :posts))))
    (r/reduce (r/lambda [acc next] (r/+ acc next)) 0))
```

#### count

`([sequence & [filter]])`

Count the number of elements in the sequence. With a single argument, count the number
of elements equal to it. If the argument is a function, it is equivalent to calling
filter before count.

```clojure
(-> (r/table "authors")
    (r/count))
```

#### distinct

`([sequence])`

Remove duplicates from the sequence.

```clojure
(-> (r/table "marvel")
    (r/mapcat (r/lambda [hero]
                (r/get-field hero :villain-list)))
    (r/distinct))
```

#### grouped-map-reduce

`([sequence grouping mapping reduction & [base]])`

Partition the sequence into groups based on the `grouping` function. The elements of
each group are then mapped using the `mapping` function and reduced using the
`reduction` function. Generalized form of group-by.

```clojure
;; Compare heroes against their weight class
(-> (r/table "marvel")
    (r/grouped-map-reduce
      (r/lambda [hero] (r/get-field :weight-class)) ; grouping
      (r/lambda [hero] (r/pluck hero :name :strength)) :mapping
      (r/lambda [acc hero]
        (r/branch (r/< (r/get-field acc :strength) ; if
                       (r/get-field hero :strength))
          hero ; then
          acc ; else
          ))
      {:name "none" :strength 0} ; base
      ))
```

#### group-by

`([sequence array operation-map])`

Groups a sequence by one or more attributes and then applies a reduction.
The third argument is a special object literal giving the kind of operation
to be performed and anay necessary arguments.

At present group-by supports the following operations
- :count - count the size of the group
- {:sum attr} - sum the values of the given attribute accross the group
- {:avg attr} - average the values of the given attribute accross the group"

```clojure
(-> (r/table "marvel")
    (r/group-by :weight-class {:avg :strength}))
```

#### contains?

`([sequence item-or-lambda1])`

Returns whether or not a sequence contains the specified value, or if functions are
provided instead, returns whether or not a sequence contains values matching all the
specified functions.

```clojure
(-> (r/table "marvel")
    (r/get "ironman")
    (r/get-field "opponents")
    (r/contains? "superman"))
```

### Document manipulation

#### pluck

`([object-or-sequence & selectors])`

Get a subset of an object by selecting some attributes to preserve,
or map that over a sequence

```clojure
(-> (r/table "marvel")
    (r/get "IronMan")
    (r/pluck :reactor-state :reactor-power))
```


## License

Copyright © 2013 Chris Allen, César Bolaños

Distributed under the Eclipse Public License, the same as Clojure.
