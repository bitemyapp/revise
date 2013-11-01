(ns bitemyapp.revise.core-test
  (:import [flatland.protobuf PersistentProtocolBufferMap])
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [robert.bruce :refer [try-try-again]]
            [flatland.protobuf.core :as pb]
            [bitemyapp.revise.connection :refer :all]
            [bitemyapp.revise.core :refer :all]
            [bitemyapp.revise.protodefs :refer [Query]]
            [bitemyapp.revise.protoengine :refer [compile-term]]
            [bitemyapp.revise.query :as r]
            [bitemyapp.revise.response :refer [inflate]]))

(defn send-random-delay
  [^PersistentProtocolBufferMap term]
  (if-let [current @current-connection]
    (let [type :START
          token (inc (:token current))
          {:keys [in out]} current]
      (send-protobuf out (pb/protobuf Query {:query term
                                             :token token
                                             :type type}))
      (Thread/sleep (rand-int 100))
      (swap! current-connection update-in [:token] inc)
      (let [r (fetch-response in)]
        (inflate r)))))

(def drop-authors (-> (r/db "test") (r/table-drop-db "authors")))
(def create-authors (-> (r/db "test") (r/table-create-db "authors")))

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

(def insert-authors (-> (r/db "test") (r/table-db "authors")
                        (r/insert authors)))

(def filter-william (-> (r/table "authors") (r/filter (r/lambda [row] (r/= (r/get-field row :name) "William Adama")))))

(def dump-response (set authors))

(def william-response (set (filter #(= (:name %) "William Adama") authors)))

(defn pare-down [docs]
  (map #(select-keys % [:name :posts :tv-show]) docs))

(defn prep-result [result]
  (set (pare-down (:response result))))

(defn dump-and-william []
  [(future (prep-result (-> (r/table "authors") (run)))) (future (prep-result (run filter-william)))])

(defn test-match-results []
  (let [[dump william] (dump-and-william)]
    (if (and (= dump-response @dump) (= william-response @william))
      (throw (ex-info "I want racy results." {:type :python-exception :cause :eels}))
      (throw (Exception. (str "RACE CONDITION! non-match for " (diff dump-response @dump) (diff william-response @william)))))))

(defn try-until-race []
  (try-try-again {:sleep nil :tries 10 :catch [clojure.lang.ExceptionInfo]} test-match-results))


(deftest ^:race-condition race-condition
  (let [conn (connect)
        drop (-> (r/db "test") (r/table-drop-db "authors") (run))
        create (-> (r/db "test") (r/table-create-db "authors") (run))
        _ (run insert-authors)]
    (testing "Can produce race condition"
      (with-redefs [;; bitemyapp.revise.core/run run-random-delay
                    bitemyapp.revise.connection/send-term send-random-delay]
        (try-until-race)))
    (testing "But I can get sane results normally"
      (let [[dump william] (map deref (dump-and-william))]
        (are [x y] (= x y)
             dump dump-response
             william william-response)))))

(deftest queries
  (testing "Can query RethinkDB"
    (let [conn (connect)

          ;; When connecting: SUCCESS
          ;; {:token 1, :response (("tv_shows"))}
          drop  (run drop-authors)
          create (run create-authors)
          insert (run insert-authors)
          dump (-> (r/table "authors") (run))
          william (run filter-william)

          posts (-> (r/table "authors") (r/filter (r/lambda [row]
                                                            (r/= 2
                                                                 (r/count (r/get-field row :posts)))))
                    (run))

          cherry-pick (-> (r/table "authors") (r/get "7644aaf2-9928-4231-aa68-4e65e31bf219") (run))

          updated (-> (r/table "authors") (r/update {:type "fictional"}) (run))

          admiral-updated (-> (r/table "authors")
                              (r/filter (r/lambda [row]
                                                  (r/= "William Adama"
                                                       (r/get-field row :name))))
                              (r/update {:rank "Admiral"})
                              (run))

          wat (-> (r/table "authors")
                  (r/filter (r/lambda [row]
                                      (r/= "Jean-Luc Picard"
                                           (r/get-field row :name))))
                  (r/update
                   (r/lambda [row]
                             {:posts
                              (r/append (r/get-field row :posts)
                                        {:title "Shakespeare"
                                         :content "What a piece of work is man.."})}))
                  (run))

          deleted (-> (r/table "authors")
                      (r/filter (r/lambda [row]
                                          (r/< (r/count (r/get-field row :posts))
                                               3)))
                      (r/delete)
                      (run))

          second-dump (-> (r/db "test") (r/table-db "authors") (run))

          closed (close conn)]
      (are [x y] (= x y)
      conn    {}
      drop    {}
      create  {}
      insert  []
      dump    []
      william []
      posts   []
      cherry-pick []
      updated []
      admiral-updated []
      wat []
      deleted []
      second-dump []
      closed {}))))

;;; I'm replacing the authors table because it doens't work with plenty of the
;;; api. I'll be removing duplicates in time.
;;; Order based on the README

;;; -----------------------------------------------------------------------
;;; Manipulating databases
(def create-database (r/db-create "revise_test_db"))
(def drop-database (r/db-drop "revise_test_db"))
(def db-list (r/db-list))
;;; -----------------------------------------------------------------------
;;; Manipulating tables
(def create-table
  (-> (r/db "test") (r/table-create-db "revise_test1")))
(def create-table-optargs
  (-> (r/db "test") (r/table-create-db "revise_users"
                                       :primary-key :name)))
(def drop-table
  (-> (r/db "test") (r/table-drop-db "revise_test1")))

(def create-index
  (-> (r/db "test") (r/table-db "revise_users")
      (r/index-create :email (r/lambda [user] (r/get-field user :email)))))
(def create-multi-index
  (-> (r/db "test") (r/table-db "revise_users")
      (r/index-create :demo
                      (r/lambda [user]
                              [(r/get-field user :age)
                               (r/get-field user :country)]))))
(def list-index
  (-> (r/db "test") (r/table-db "revise_users") (r/index-list)))
(def drop-index
  (-> (r/db "test") (r/table-db "revise_users") (r/index-drop :email)))
;;; -----------------------------------------------------------------------
;;; Writing data
(def users (-> (r/db "test") (r/table-db "revise_users")))
(def data-multi
  [{:name "aa" :age 20 :country "us" :email "aa@ex.com"
    :gender "m" :posts ["a" "aa" "aaa"]
    :permission 1}
   {:name "bb" :age 21 :country "us" :email "bb@ex.com"
    :gender "f" :posts ["b" "bb"]
    :permission 1}
   {:name "cc" :age 20 :country "mx" :email "cc@ex.com"
    :gender "m" :posts ["c"]
    :permission 3}
   {:name "dd" :age 20 :country "ca" :email "dd@ex.com"
    :gender "m" :posts ["dddd"]
    :permission 3}
   {:name "ee" :age 21 :country "ca" :email "ee@ex.com"
    :gender "f" :posts ["e" "ee" "e" "eeeee"]
    :permission 2}
   {:name "ff" :age 22 :country "fr" :email "ff@ex.com"
    :gender "a" :posts []
    :permission 2}])
(def data-single
  {:name "gg" :age 21 :country "in" :email "gg@ex.com"
   :gender "b" :posts [] :permission 3})
(def insert-multi
  (-> users (r/insert data-multi)))
(def insert-single
  (-> users (r/insert data-single)))

(def update-append
  (-> users (r/update {:admin false})))
(def update-lambda
  (-> users (r/update (r/lambda [user]
                                {:age
                                 (r/+ 1 (r/get-field user :age))}))))

(def replace-test
  (-> users (r/get "dd")
      (r/replace
       {:name "dd" :age 13 :country "ru" :email "hax@pwned.com"
        :admin true})))
(def delete
  (-> users (r/get "dd")
      (r/delete)))
;;; -----------------------------------------------------------------------
;;; Selecting data
(def reference-db
  (r/db "test"))
(def select-table
  users)
(def get-doc
  (-> users (r/get "aa")))
(def get-all
  (-> users (r/get-all ["aa" "bb"])))
(def between
  (-> users (r/between "aa" "dd")))
(def filter-test
  (-> users (r/filter (r/lambda [user] (r/= 21
                                            (r/get-field user :age))))))
;;; -----------------------------------------------------------------------
;;; Joins
(def create-permissions
  (-> (r/db "test") (r/table-create-db "revise_permissions" :primary-key :number)))
(def permissions-data
  [{:number 1 :admin false :permission "read"}
   {:number 2 :admin false :permission "write"}
   {:number 3 :admin false :permission "execute"}])
(def permissions
  (-> (r/db "test") (r/table-db "revise_permissions")))
(def add-permissions
  (-> permissions (r/insert permissions-data)))
(def inner-join
  (-> users (r/inner-join permissions
                          (r/lambda [user perm]
                                    (r/= (r/get-field user :admin)
                                         (r/get-field perm :admin))))))
(def outer-join
  (-> users (r/outer-join permissions
                          (r/lambda [user perm]
                                    (r/= (r/get-field user :admin)
                                         (r/get-field perm :admin))))))
(def eq-join
  (-> users (r/eq-join :permission permissions :number)))
(def zip
  (-> eq-join r/zip))
;;; -----------------------------------------------------------------------
;;; Transformations
(def mapped
  (-> users (r/map (r/lambda [user] (r/get-field user :age)))))
(def with-fields
  (-> users (r/with-fields :email :country)))
(def mapcatted
  (-> users (r/mapcat (r/lambda [user]
                                (r/get-field user :posts)))))
(def ordered-desc
  (-> users (r/order-by (r/desc :age))))
(def ordered-asc
  (-> users (r/order-by (r/asc :age))))
(def ordered
  (-> users (r/order-by :age)))
(def skip
  (-> ordered
      (r/skip 2)))
(def limit
  (-> ordered
      (r/limit 2)))
(def slice
  (-> ordered
      (r/slice 1 3)))
(def nth-item
  (-> ordered
      (r/nth 1)))
(def indexes-of
  (r/indexes-of ["a" "b" "a" "c" "a"] "a"))
(def empty-array
  (r/empty? []))
(def union
  (-> users
      (r/union permissions)))
(def sample
  (-> users
      (r/sample 2)))
;;; -----------------------------------------------------------------------
;;; Aggregation
(def count-posts
  (-> users
      (r/map (r/lambda [user] (r/count (r/get-field user :posts))))
      (r/reduce (r/lambda [acc cnt] (r/+ acc cnt)) 0)))
(def distinct-array
  (r/distinct [1 1 2 2 3 3 4 4]))
(def grouped-map-reduce
  (-> users
      (r/grouped-map-reduce
       (r/lambda [user] (r/get-field user :age))
       (r/lambda [user] (r/count (r/get-field user :posts)))
       (r/lambda [acc cnt]
                 (r/+ acc cnt))
       0)))
(def grouped-count
  (-> users
      (r/group-by [:age] :count)))
(def grouped-sum
  (-> users
      (r/group-by [:country] {:sum :permission})))
(def grouped-average
  (-> users
      (r/group-by [:country] {:avg :age})))
(def contains
  (-> users
      (r/get "aa")
      (r/get-field :posts)
      (r/contains? "aa")))
;;; -----------------------------------------------------------------------
;;; Document manipulation
(def pluck
  (-> users (r/get "aa") (r/pluck :name :age)))
(def without
  (-> users (r/get "aa") (r/pluck :posts :country :gender :email)))
(def merge-test
  (-> users
      (r/get "aa")
      (r/merge (-> permissions (r/limit 1)))))
(def append
  (-> users
      (r/get "aa")
      (r/update (r/lambda [user]
                          {:posts
                           (r/append (r/get-field user :posts)
                                     "wheee")}))))
(def prepend
  (-> users
      (r/get "aa")
      (r/update (r/lambda [user]
                          {:posts
                           (r/prepend (r/get-field user :posts)
                                      "aaaah")}))))
(def difference
  (r/difference
   (-> users
       (r/get "aa")
       (r/get-field :posts))
   ["a" "aa" "aaa"]))
(def set-insert
  (r/set-insert [1 1 2] 3))
(def set-union
  (r/set-union [1 2 3] [2 3 4]))
(def set-intersection
  (r/set-insert [1 2 3] [3 4 5]))
(def get-field
  (-> users
      (r/get "aa")
      (r/get-field :name)))
(def has-fields
  (-> users
      (r/get "aa")
      (r/has-fields? :name :email :posts)))
(def insert-at
  (r/insert-at [1 3 4 5] 1 2))
(def splice-at
  (r/splice-at [1 2 6 7] 2 [3 4 5]))
(def delete-at
  (r/delete-at [1 2 3 4 5] 1 3))
(def change-at
  (r/change-at [1 2 5 4 5] 2 3))
(def keys-test
  (-> users
      (r/get "aa")
      (r/keys)))
;;; -----------------------------------------------------------------------
;;; String Manipulation
(def match-string
  (-> (r/filter ["Hello" "Also" "Goodbye"]
                (r/lambda [s]
                          (r/match s #"^A")))))
;;; -----------------------------------------------------------------------
;;; Math and Logic
(def math
  (r/mod 7
         (r/+ 1
              (r/* 2
                   (r/div 4 2)))))
;; (def and-test
;;   (r/and true true true))
;; (def or-test
;;   (r/or false false true))
(def =test
  (r/= 1 1))
(def not=test
  (r/not= 1 2))
(def >test
  (r/> 5 2))
(def >=test
  (r/>= 5 5))
(def <test
  (r/< 2 5))
(def <=test
  (r/<= 5 5))
(def notatest
  (r/not false))
;;; -----------------------------------------------------------------------
;;; Dates and Times
(def now
  (r/now))
(def time-test
  (r/time 2005 10 20 3 40 5.502 "-06:00"))
(def epoch-time
  (r/epoch-time 531360000))
(def iso8601
  (-> (r/iso8601 "2005-10-20T03:40:05.502-06:00")))
(def in-timezone
  (r/in-timezone time-test "-07:00"))
(def timezone
  (r/timezone time-test))
(def during
  (r/during time-test (r/time 2005 10 19 "-06:00") (r/time 2005 10 21 "-06:00")))
(def date
  (r/date time-test))
(def time-of-day
  (r/time-of-day time-test))
(def ->iso8601
  (r/->iso8601 time-test))
(def ->epoch-time
  (r/->epoch-time time-test))
;;; -----------------------------------------------------------------------
;;; Access time fields
(def year
  (r/year time-test))
(def month
  (r/month time-test))
(def day
  (r/day time-test))
(def day-of-week
  (r/day-of-week time-test))
(def day-of-year
  (r/day-of-year time-test))
(def hours
  (r/hours time-test))
(def minutes
  (r/minutes time-test))
(def seconds
  (r/seconds time-test))
;;; -----------------------------------------------------------------------
;;; Control structures
(def branch
  (r/branch true
            "tis true!"
            "tis false!"))
(def any
  (r/any false false false true))
(def all
  (r/all true true true true))
(def foreach
  ;; TODO
  )
(def error
  (r/error "Wheeee"))
(def default
  (r/default nil "oooooh"))
(def parse-val
  (r/parse-val [1 false "hello" :goodbye {:a 1}]))
(def js
  (r/js "1 + 1"))
(def coerce-to
  (r/coerce-to {:a 1} :array))
(def type-test
  (r/type [1 2 3]))
(def info
  (r/info users))
(def json
  (r/json "[1,2,3]"))
;;; -----------------------------------------------------------------------
;;; Control structures
(def time-constants
  (r/parse-val [r/monday r/tuesday r/wednesday r/thursday r/friday
                r/saturday r/sunday
                r/january r/february r/march r/april r/may r/june r/july
                r/august r/september r/october r/november r/december]))

;;; MODIFY THIS AFTER PROMISES STUFF IS WORKING :-)?
(defn resp [r] (:response r))
(def rr (comp resp run))

(deftest queries
  (testing "Manipulating databases"
    (is (= (rr create-database) [{:created 1}]))
    (is (contains? (set (first (rr db-list)))
                   "revise_test_db"))
    (is (= (rr drop-database)   [{:dropped 1}])))

  (testing "Manipulating tables"
    (are [x y] (= x y)
         (rr create-table)                 [{:created 1}]
         (rr create-table-optargs)         [{:created 1}]
         (rr drop-table)                   [{:dropped 1}]
         (rr create-index)                 [{:created 1}]
         (rr create-multi-index)           [{:created 1}]
         (set (first (rr list-index)))     #{"demo" "email"}
         (rr drop-index)                   [{:dropped 1}]))

  (testing "Writing data"
    (are [x y] (= x y)
         (:inserted (first (rr insert-multi)))     6
         (:inserted (first (rr insert-single)))    1
         (:replaced (first (rr update-append)))    7
         (:replaced (first (rr update-lambda)))    7
         (:replaced (first (rr replace-test)))     1
         (:deleted (first (rr delete)))            1))

  (testing "Selecting data"
    (are [x y] (= x y)
         (:error (run reference-db))               :runtime-error
         (count (rr select-table))                 6
         (first (rr get-doc))                      {:admin false :age 21,
                                                    :country "us"
                                                    :email "aa@ex.com"
                                                    :gender "m" :name "aa"
                                                    :posts ["a" "aa" "aaa"]
                                                    :permission 1}
         (count (rr get-all))                      2
         (count (rr between))                      3
         (count (rr filter-test))                  2
         (first (rr create-permissions))           {:created 1}
         (:inserted (first (rr add-permissions)))  3))

  (testing "Joins"
    (are [x y] (= x y)
         ;; 6 x 3 = 18 - cartesian product
         (count (rr inner-join))    18
         (count (rr outer-join))    18
         (count (rr eq-join))       6
         (count (rr zip))           6))

  (testing "Transformations"
    (are [x y] (= x y)
         (set (rr mapped))                    #{21 22 23}
         (count (rr with-fields))             6
         (set (rr mapcatted))                 #{"aa" "bb" "ee" "aaa"
                                                "a" "b" "c" "e" "eeeee"}
         (:age (first (rr ordered-desc)))     23
         (:age (first (rr ordered-asc)))      21
         (count (rr ordered))                 6
         (count (rr skip))                    4
         (count (rr limit))                   2
         (count (rr slice))                   2
         (:age (first (rr nth-item)))         21
         (first (rr indexes-of))              [0 2 4]
         (rr empty-array)                     [true]
         (count (rr union))                   9
         (count (rr sample))                  2))

  (testing "Aggregation"
    (are [x y] (= x y)
         (first (rr count-posts))             10
         (set (first (rr distinct-array)))    #{1 2 3 4}
         (first (rr grouped-map-reduce))      [{:group 21, :reduction 4}
                                               {:group 22, :reduction 6}
                                               {:group 23, :reduction 0}]
         (first (rr grouped-count))           [{:group {:age 21}, :reduction 2}
                                               {:group {:age 22}, :reduction 3}
                                               {:group {:age 23}, :reduction 1}]
         ;; TODO - grouped-sum + grouped-average
         (first (rr contains))                true))

  (testing "Document Manipulation"
    (are [x y] (= x y)
         (first (rr pluck))                        {:age 21, :name "aa"}
         (first (rr without))                      {:country "us"
                                                    :email "aa@ex.com"
                                                    :gender "m"
                                                    :posts ["a" "aa" "aaa"]}
         (:replaced (first (rr append)))           1
         (:replaced (first (rr prepend)))          1
         (:posts (first (rr (r/get users "aa"))))  ["aaaah" "a" "aa" "aaa"
                                                    "wheee"]
         (first (rr difference))                   ["aaaah" "wheee"]
         (rr set-insert)                           [[1 2 3]]
         (rr set-union)                            [[1 2 3 4]]
         ;; wtf
         ;(rr set-intersection) [[1 2 3 [4 5 6]]] ??
         (rr get-field)                ["aa"]
         (rr has-fields)               [true]
         (rr insert-at)                [[1 2 3 4 5]]
         (rr splice-at)                [[1 2 3 4 5 6 7]]
         (rr delete-at)                [[1 4 5]]
         (rr change-at)                [[1 2 3 4 5]]
         (set (first (rr keys-test)))  #{"gender" "name" "permission"
                                         "admin" "posts" "country" "email" "age"}))

  (testing "String Manipulation"
    (is (= (rr match-string) [["Also"]])))

  (testing "Math and Logic"
    (are [x y] (= x y)
         (rr math)      [2]
         (rr =test)     [true]
         (rr not=test)  [true]
         (rr >test)     [true]
         (rr >=test)    [true]
         (rr <test)     [true]
         (rr <=test)    [true]
         (rr notatest)  [true]))
  ;; HMMM
  ;; (testing "Dates and Times"
  ;;   (are [x y] (= x y)

  ;;        (set (keys (first (rr now))))  #{:epoch_time :reql_type :timezone}
  ;;        (first (rr time-test))         {:reql_type "TIME",
  ;;                                        :epoch_time 1.129801205502E9,
  ;;                                        :timezone "-06:00"}
  ;;        (first (rr epoch-time))        {:reql_type "TIME",
  ;;                                        :epoch_time 531360000,
  ;;                                        :timezone "+00:00"}
  ;;        (first (rr iso8601))           {:reql_type "TIME",
  ;;                                        :epoch_time 1.129801205502E9,
  ;;                                        :timezone "-06:00"}
  ;;        (first (rr in-timezone))       {:reql_type "TIME",
  ;;                                        :epoch_time 1.129801205502E9,
  ;;                                        :timezone "-07:00"}
  ;;        (rr timezone)                  ["-06:00"]
  ;;        (rr during)                    [true]
  ;;        (first (rr date))              {:reql_type "TIME",
  ;;                                        :epoch_time 1129766400,
  ;;                                        :timezone "-06:00"}
  ;;        (rr time-of-day)               [34805.502]
  ;;        (rr ->iso8601)                 ["2005-10-20T03:40:05.502-06:00"]
  ;;        (rr ->epoch-time)              [1129801205]))

  (testing "Time fields access"
    (are [x y] (= x y)
         (rr year)        [2005]
         (rr month)       [10]
         (rr day)         [20]
         (rr day-of-week) [4]
         (rr day-of-year) [293]
         (rr hours)       [3]
         (rr minutes)     [40]
         (rr seconds)     [5.502]))

  (testing "Control structures"
    (are [x y] (= x y)
         (rr branch)        ["tis true!"]
         (rr any)           [true]
         (rr all)           [true]
         (rr error)         ["Wheeee"]
         (rr default)       ["oooooh"]
         ;; parse-val fails why? should it be make-array??
         ; (rr parse-val)
         (rr js)            [2]
         (rr coerce-to)     [[["a" 1]]]
         (rr type-test)     ["ARRAY"]
         (first (rr info))  {:db {:name "test", :type "DB"}
                             :indexes ["demo"], :name "revise_users"
                             :primary_key "name", :type "TABLE"}
         (rr json)          [[1 2 3]]))

  (testing "Time constants"
    (is (= (first (rr time-constants))
           [1 2 3 4 5 6 7 1 2 3 4 5 6 7 8 9 10 11 12])))

  (testing "Cleanup"
    (are [x y] (= x y)
         (first (rr (-> (r/db "test")
                        (r/table-drop-db "revise_users")))) {:dropped 1}
         (first (rr (-> (r/db "test")
                        (r/table-drop-db "revise_permissions")))) {:dropped 1})))
