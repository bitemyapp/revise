(ns revise.core
  (:use [clojure.java.io]
        [flatland.protobuf.core]
        [gloss.core]
        [gloss.io])
  (:import [java.net Socket]
           [java.io BufferedInputStream DataInputStream BufferedOutputStream]
           [java.io InputStream InputStreamReader BufferedReader OutputStream])
  (:gen-class :main true))

(import Rethinkdb$VersionDummy)
(import Rethinkdb$Response)
(import Rethinkdb$Query)
(import Rethinkdb$Datum)
(import Rethinkdb$Term)
(import Rethinkdb$Backtrace)
(import Rethinkdb$Frame)

(def version-frame (compile-frame {:a :int32-le}))
(defn gen-ver [val]
  (.array (contiguous (encode version-frame {:a val}))))

(def VersionDummy (protodef Rethinkdb$VersionDummy))
(def Response (protodef Rethinkdb$Response))
(def Datum (protodef Rethinkdb$Datum))
(def Query (protodef Rethinkdb$Query))
(def Frame (protodef Rethinkdb$Frame))
(def Backtrace (protodef Rethinkdb$Backtrace))
(def Term (protodef Rethinkdb$Term))

(def db-host {:name "localhost" :port 28015})

;; (defn pb [& args]
;;   (protobuf args))
;; (defn pbt [& args]
;;   (pb Term args))
;; (defn pbd [& args]
;;   (pb Datum args))

;; (defn list-databases []
;;   (protobuf-dump
;;    (pbt :type :DB_LIST)))

(defn list-databases []
  (protobuf-dump (protobuf Term :type :DB_LIST)))

(defn insert-example []
  (protobuf-dump
   (protobuf Term
             :type :INSERT
             :args [(protobuf Term
                              :type :TABLE
                              :args [(protobuf Term
                                               :type :DATUM
                                               :r_datum (protobuf Datum
                                                                  :type :R_STR
                                                                  :r_str "test"))]
                              :optargs [["use_outdated" (protobuf Term
                                                                  :type :DATUM
                                                                  :r_datum (protobuf Datum :type :R_BOOL :r_bool true))]])
                    (protobuf Term
                              :type :MAKE_ARRAY
                              :args [(protobuf Term
                                               :type :DATUM
                                               :r_datum (protobuf Datum
                                                                  :type :R_OBJECT
                                                                  :r_object [["id", 0]]))
                                     (protobuf Term
                                               :type :DATUM
                                               :r_datum (protobuf Datum
                                                                  :type :R_OBJECT
                                                                  :r_object [["id", 1]]))])])))

(def magic-number 1063369270)
(def version (protobuf-dump (protobuf VersionDummy :Version magic-number)))
(def ver-frame (gen-ver magic-number))
;; (println (str "ver-frame: " (.get ver-frame)))
;; 0x3f61ba36

(declare conn-handler)
(declare db)

(defn connect [server]
  (let [socket (Socket. (:name server) (:port server))
        in (DataInputStream. (BufferedInputStream. (.getInputStream socket)))
        out (BufferedOutputStream. (.getOutputStream socket))
        conn (ref {:in in :out out})]
    (doto (Thread. #(conn-handler conn)) (.start))
    conn))

(defn conn-handler [conn]
  (println "Conn handler started")
  (while (nil? (:exit @conn))
    (let [data (.read (:in @conn))]
      (println "let data")
      (when data
        (println "when data")
        (let [pb (protobuf-load Response data)
              bpb (bean pb)]
          (println "data read in")
          (println data)
          (println pb)
          (println bpb))))))

(defn write [conn data]
  (println (str "outgoing: " data))
  (doto (:out @conn)
    (.write data)
    (.flush)))

(defn -main [& args]
  ;; (write rdb magic-number)
  (println "pre ver frame")
  ; (def db (connect db-host))
  (write db ver-frame)
  ;; (write db (insert-example))
  (println "pre list databases")
  (write db (list-databases)))
