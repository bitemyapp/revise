(ns bitemyapp.revise.utils.seq)

;; This doesn't really help much. We need reducers.
(defn join
  "Lazily concatenates a sequence-of-sequences into a flat sequence.
 ( http://dev.clojure.org/jira/browse/CLJ-1218 )"
  [s]
  (lazy-seq (when-let [[x & xs] (seq s)] (concat x (join xs)))))
