(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send compile])
  (:require [bitemyapp.revise.connection :refer [connect close send]]
            [bitemyapp.revise.protoengine :refer [compile]]
            [bitemyapp.revise.query :as r]))

(defn run
  [q]
  (send (compile q)))

(defn -main
  []
  (connect)
  (prn (-> (r/db "test") (r/table-list-db) (run)))
  (close))

;; Prints

;; When connecting: SUCCESS
;; {:token 1, :response (("tv_shows"))}

;;; The ten-minute guide
(comment
  (connect)
  ;; 1
  (-> (r/db "test") (r/table-create-db "authors") (run))
  ;; 2
  (-> (r/db "test") (r/table-db "authors")
      (r/insert [{:name "William Adama" :tv-show "Battlestar Galactica"
                  :posts [{:title "Decommissioning speech",
                           :content "The Cylon War is long over..."},
                          {:title "We are at war",
                           :content "Moments ago, this ship received word..."},
                          {:title "The new Earth",
                           :content "The discoveries of the past few days..."}]}

                 {:name "Laura Roslin", :tv_show "Battlestar Galactica",
                  :posts [{:title "The oath of office",
                           :content "I, Laura Roslin, ..."},
                          {:title "They look like us",
                           :content "The Cylons have the ability..."}]}

                 {:name "Jean-Luc Picard", :tv_show "Star Trek TNG",
                  :posts [{:title "Civil rights",
                           :content "There are some words I've known since..."}]}])
      (run))
  ;; 3
  (-> (r/table "authors") (run))
  ;; 4
  (-> (r/table "authors") (r/filter (r/lambda [row]
                                              (r/= (r/get-field row :name)
                                                   "William Adama")))
      (run))
  ;; 5
  (-> (r/table "authors") (r/filter (r/lambda [row]
                                              (r/= 2
                                                   (r/count (r/get-field row :posts)))))
      (run))
  ;; 6
  (-> (r/table "authors") (r/get "7644aaf2-9928-4231-aa68-4e65e31bf219") (run))
  ;; 7
  (-> (r/table "authors") (r/update {:type "fictional"}) (run))
  ;; 8
  (-> (r/table "authors")
      (r/filter (r/lambda [row]
                          (r/= "William Adama"
                               (r/get-field row :name))))
      (r/update {:rank "Admiral"})
      (run))
  ;; 9 What the hell
  (-> (r/table "authors")
      (r/filter (r/lambda [row]
                          (r/= "Jean-Luc Picard"
                               (r/get-field row :name))))
      (r/update
       (r/lambda [row]
                 {:posts ;; can only merge messages of the same type
                  (r/append (r/get-field row :posts)
                            {:title "Shakespeare"
                             :content "What a piece of work is man.."})}))
      (run))
  ;; 10
  (-> (r/table "authors")
      (r/filter (r/lambda [row]
                          (r/< (r/count (r/get-field row :posts))
                               3)))
      (r/delete)
      (run))

  (-> (r/table "authors") (run))

  (close))
