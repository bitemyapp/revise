(defproject revise "0.0.1-SNAPSHOT"
  :description "RethinkDB client for Clojure"
  :url "github.com/bitemyapp/revise/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main bitemyapp.revise.core
  :plugins [[lein-protobuf "0.3.1"]
            [lein-difftest "2.0.0"]]
  :test-selectors {:default (fn [_] true) ;; (complement :integration)
                   :race-condition :race-condition
                   :all (fn [_] true)}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [robert/bruce "0.7.1"]
                 [gloss "0.2.2-beta4"]
                 [javert "0.2.0-SNAPSHOT"]
                 [org.flatland/protobuf "0.7.2"]]
  :repl-options {:port 7779})
