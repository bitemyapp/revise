(ns revise.core
  (:use [clojure.java.io]
        [flatland.protobuf.core]
        [gloss.core]
        [gloss.io])
  (:import [java.net Socket]
           [java.io InputStreamReader BufferedReader OutputStream])
  (:gen-class :main true))

(import Rethinkdb$VersionDummy)

(def version-frame (compile-frame {:a :int32-le}))
(defn gen-ver [val]
  (contiguous (encode version-frame {:a val})))

(def VersionDummy (protodef Rethinkdb$VersionDummy))

(def db-host {:name "localhost" :port 28015})

(def magic-number 1063369270)
(def version (protobuf-dump (protobuf VersionDummy :Version magic-number)))
(def ver-frame (gen-ver magic-number))
;; (println (str "ver-frame: " (.get ver-frame)))
;; 0x3f61ba36

(declare conn-handler)

(defn connect [server]
  (let [socket (Socket. (:name server) (:port server))
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out (.getOutputStream socket)
        conn (ref {:in in :out out})]
    (doto (Thread. #(conn-handler conn)) (.start))
    conn))

(defn write [conn data]
  (println (str "outgoing: " data))
  (doto (:out @conn)
    (.write data)
    (.flush)))

(defn conn-handler [conn]
  (while (nil? (:exit @conn))
    (let [data (.readLine (:in @conn))]
      (when data
        (println data)))))

(def db (connect db-host))

(defn -main [& args]
  ;; (write rdb magic-number)
  (write db ver-frame)
  (conn-handler db))
