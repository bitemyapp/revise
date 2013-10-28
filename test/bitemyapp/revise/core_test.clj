(ns bitemyapp.revise.core-test
  (:refer-clojure :exclude [compile send])
  (:import [flatland.protobuf PersistentProtocolBufferMap])
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [robert.bruce :refer [try-try-again]]
            [flatland.protobuf.core :as pb]
            [bitemyapp.revise.connection :refer :all]
            [bitemyapp.revise.core :refer :all]
            [bitemyapp.revise.protodefs :refer [Query]]
            [bitemyapp.revise.protoengine :refer [compile]]
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
                    bitemyapp.revise.connection/send send-random-delay]
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
