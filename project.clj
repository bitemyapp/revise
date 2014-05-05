(defproject revise "0.1.0-SNAPSHOT"
  :description "RethinkDB client for Clojure"
  :url "github.com/bitemyapp/revise/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main bitemyapp.revise.core
  :plugins [[com.jakemccrary/lein-test-refresh "0.1.2"]
            [lein-difftest "2.0.0"]]
  :test-selectors {:default (fn [_] true) ;; (complement :integration)
                   :race-condition :race-condition
                   :all (fn [_] true)}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [robert/bruce "0.7.1"]
                 [revise/protobuf "0.8.3"]
                 [revise/rethinkdb "1.0.2"]])
