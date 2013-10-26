(ns bitemyapp.revise.core-test
  (:refer-clojure :exclude [send])
  (:require [clojure.test :refer :all]
            [bitemyapp.revise.connection :refer :all]
            [bitemyapp.revise.core :refer :all]
            [bitemyapp.revise.query :as r]))

(deftest database-connections
  (testing "Can connect to RethinkDB"
    (is (= 0 1))))

(deftest queries
  (testing "Can query RethinkDB"
    (let [conn (connect)

          ;; When connecting: SUCCESS
          ;; {:token 1, :response (("tv_shows"))}

          drop (-> (r/db "test") (r/table-drop-db "authors") (run))

          create (-> (r/db "test") (r/table-create-db "authors") (run))

          insert (-> (r/db "test") (r/table-db "authors")
                     (r/insert [{:name "William Adama" :tv-show "Battlestar Galactica"
                                 :posts [{:title "Decommissioning speech",
                                          :rating 3.5
                                          :content "The Cylon War is long over..."},
                                         {:title "We are at war",
                                          :content "Moments ago, this ship received word..."},
                                         {:title "The new Earth",
                                          :content "The discoveries of the past few days..."}]}

                                {:name "Laura Roslin", :tv_show "Battlestar Galactica",
                                 :posts [{:title "The oath of office",
                                          :rating 4
                                          :content "I, Laura Roslin, ..."},
                                         {:title "They look like us",
                                          :content "The Cylons have the ability..."}]}

                                {:name "Jean-Luc Picard", :tv_show "Star Trek TNG",
                                 :posts [{:title "Civil rights",
                                          :content "There are some words I've known since..."}]}])
                     (run))

          dump (-> (r/table "authors") (run))

          william (-> (r/table "authors") (r/filter (r/lambda [row]
                                                              (r/= (r/get-field row :name)
                                                                   "William Adama")))
                      (run))

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
