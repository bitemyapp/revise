(ns bitemyapp.revise.utils.case
  "Snake case fns"
  (:require [clojure.string :as s]))

(defprotocol IStringy
  (snake-case [x] "Turn into snake case string")
  ;; Maybe camel case here
  )

(defn split
  [s]
  (->>
   (s/split s (re-pattern "_|-| |(?<=[A-Z])(?=[A-Z][a-z])|(?<=[^A-Z_])(?=[A-Z])|(?<=[A-Za-z])(?=[^A-Za-z])"))
   (filter seq)
   (map s/lower-case)))

(extend-protocol IStringy
  java.lang.String
  (snake-case [s]
    (let [s (split s)]
      (s/join "_" s)))

  clojure.lang.Keyword
  (snake-case [k]
    (snake-case (name k))))

(defn lower-case
  [x]
  (if (keyword? x)
    (-> x (name) (s/lower-case) (keyword))
    (-> x (s/lower-case))))
