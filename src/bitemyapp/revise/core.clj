(ns bitemyapp.revise.core
  "Testing stuff"
  (:refer-clojure :exclude [send])
  (:require [bitemyapp.revise.connection :refer [connect close send]]
            [bitemyapp.revise.protoengine :refer [->proto]]
            [bitemyapp.revise.query :refer [new-query db table-list
                                            table-create
                                            table-drop]]))

(defn run
  [q]
  (send (->proto q)))

(defn -main
  []
  (connect)
  (pr (-> (new-query) (db "test") (table-list) (run)))
  (println)
  (pr (-> (new-query) (db "test") (table-create "foobar") (run)))
  (println)
  (pr (-> (new-query) (db "test") (table-list) (run)))
  (println)
  (pr (-> (new-query) (db "test") (table-drop "foobar") (run)))
  (println)
  (pr (-> (new-query) (db "test") (table-list) (run)))
  (close))

;; Prints

;; When connecting: SUCCESS
;; {:token 1, :response (("tv_shows"))}
;; {:token 2, :response ({:created 1.0})}
;; {:token 3, :response (("tv_shows" "foobar"))}
;; {:token 4, :response ({:dropped 1.0})}
;; {:token 5, :response (("tv_shows"))}
