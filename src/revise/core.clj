(ns revise.core
  (:use [clojure.java.io]
        [flatland.protobuf.core]
        [gloss.core]
        [gloss.io])
  (:import [java.net Socket]
           [java.io PrintWriter InputStreamReader BufferedReader OutputStream])
  (:gen-class :main true))

(import Rethinkdb$VersionDummy)

(def version-frame (compile-frame {:a :int32-le}))
(defn gen-ver [val]
  (contiguous (encode version-frame {:a val})))

(def VersionDummy (protodef Rethinkdb$VersionDummy))

(def host {:name "localhost" :port 28015})

(def magic-number 1063369270)
(def version (protobuf-dump (protobuf VersionDummy :Version magic-number)))
(def ver-frame (gen-ver magic-number))
;; (println (str "ver-frame: " (.get ver-frame)))
;; 0x3f61ba36

(declare conn-handler)

(defn connect [server]
  (let [socket (Socket. (:name server) (:port server))
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out (PrintWriter. (.getOutputStream socket))
        conn (ref {:in in :out out})]
    (doto (Thread. #(conn-handler conn)) (.start))
    conn))

(defn write [conn msg]
  ; (println (str "being sent: " msg))
  (doto (:out @conn)
    (.write msg)
    (.flush)))

(defn conn-handler [conn]
  (while (nil? (:exit @conn))
    (let [msg (.readLine (:in @conn))]
      (when msg
        (println msg)))))

;; (def rdb (connect host))

(def sock (java.net.Socket. "localhost" 28015))

(def write-stream (writer sock))
(def read-stream  (reader sock))

(defn read-loop []
  (loop [buffer ""]
    ;;(println (str "Looping.. -> " buffer))
    (let [nbuf (parseMessage buffer)
          nchr (.read read-stream)  ]
      (if-not (= nchr -1)
        (recur (str nbuf (char nchr)))))))

(defn write-s [message]
    (.write write-stream message)
    (println (str "-> " message))
    (.flush write-stream))

(defn -main [& args]
  ;; (write rdb magic-number)
  (write-s magic-number)
  (read-loop))
