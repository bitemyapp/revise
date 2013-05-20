(ns bitemyapp.revise.core
  (:use [clojure.java.io]
        [flatland.protobuf.core]
        [gloss.core]
        [gloss.io])
  (:import [java.net Socket ConnectException]
           [java.nio ByteOrder ByteBuffer]
           [flatland.protobuf PersistentProtocolBufferMap]
           [java.io DataInputStream DataOutputStream])
           ;; [java.io BufferedInputStream DataInputStream BufferedOutputStream]
           ;; [java.io BufferedReader InputStream InputStreamReader OutputStream])
  (:require [flatland.protobuf.core :as pb])
  (:gen-class :main true))

(import Rethinkdb$VersionDummy)
(import Rethinkdb$Response)
(import Rethinkdb$Query)
(import Rethinkdb$Datum)
(import Rethinkdb$Term)
(import Rethinkdb$Backtrace)
(import Rethinkdb$Frame)

(def VersionDummy (protodef Rethinkdb$VersionDummy))
(def Response (protodef Rethinkdb$Response))
(def Datum (protodef Rethinkdb$Datum))
(def Query (protodef Rethinkdb$Query))
(def Frame (protodef Rethinkdb$Frame))
(def Backtrace (protodef Rethinkdb$Backtrace))
(def Term (protodef Rethinkdb$Term))

(def db-host {:name "localhost" :port 28015})

(defn to-little-endian-32
  [n]
  (let [buf (ByteBuffer/allocate 4)]
    (doto buf
      (.order ByteOrder/BIG_ENDIAN)
      (.putInt n)
      (.order ByteOrder/LITTLE_ENDIAN))
    (.getInt buf 0)))

(defn to-byte-32
  [n]
  (let [buf (ByteBuffer/allocate 4)]
    (.putInt buf n)
    (.array buf)))

(defn to-little-endian-byte-32
  [n]
  (-> n
      to-little-endian-32
      to-byte-32))

(defn parse-little-endian-32
  [b]
  (let [buf (ByteBuffer/allocate 4)]
    (doto buf
      (.order ByteOrder/LITTLE_ENDIAN)
      (.put b))
    (.getInt buf 0)))

(defn join-byte-arrays
  [a b]
  (let [size-a (count a)
        size (+ size-a (count b))
        arr (byte-array size)]
    (dotimes [i size]
      (aset-byte arr i
                 (if (> size-a i)
                   (aget a i)
                   (aget b (- i size-a)))))
    arr))

(def version-frame (compile-frame {:a :int32-le}))
(defn gen-ver [val]
  (.array (contiguous (encode version-frame {:a val}))))

;; (defn pb [& args]
;;   (protobuf args))
;; (defn pbt [& args]
;;   (pb Term args))
;; (defn pbd [& args]
;;   (pb Datum args))

;; (defn list-databases []
;;   (protobuf-dump
;;    (pbt :type :DB_LIST)))

(def list-example (protobuf Term :type :DB_LIST))
(def list-example (protobuf Term :type :TABLE_LIST))
(def list-example [(protobuf Term :type :DB :DB "example") (protobuf Term :type :TABLE_LIST)])

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
(def token (atom 1))

(def version (protobuf VersionDummy :Version magic-number))
(def ver-frame (gen-ver magic-number))
;; (println (str "ver-frame: " (.get ver-frame)))
;; 0x3f61ba36

(defn close-connection [socket]
  (.close socket))

(defn parse-response
  [#^DataInputStream in]
  (let [size (byte-array 4)]
    (.read in size 0 4)
    (let [size (parse-little-endian-32 size)
          resp (byte-array size)]
      (.read in resp 0 size)
      (pb/protobuf-load Response resp))))

(declare coerce)
(declare coerce-seq)
(declare coerce-map)

(defn coerce-seq [data-seq]
  (map coerce data-seq))

(defn coerce-map [data-map]
  (let [data-type (:type data-map)]
    (cond
     (= data-type :r-array) (coerce-seq (:r-array data-map))
     (= data-type :r-str) (:r-str data-map)
     :else (do
             (println data-map)
             (println data-type)
             (throw (Throwable. "I still don't know"))))))

(defn coerce [data]
  (let [data-type (type data)]
    (cond
     (seq? data) (coerce-seq data)
     (= data-type clojure.lang.PersistentVector) (coerce-seq data)
     (= data-type clojure.lang.PersistentArrayMap) (coerce-map data)
     (= data-type flatland.protobuf.PersistentProtocolBufferMap) (coerce-map data)
     :else (do
             (println data)
             (println data-type)
             (throw (Throwable. "Fuck I don't know."))))))

(defn coerce-response [response]
  (flatten (coerce (:response response))))

(defn make-query
  [#^PersistentProtocolBufferMap term]
  (pb/protobuf Query {:query term
                      :token @token
                      :type :START}))

(defn send-term
  [#^DataOutputStream out #^PersistentProtocolBufferMap term]
  (let [msg (pb/protobuf-dump (make-query term))
        c (count msg)
        full-msg (join-byte-arrays (to-little-endian-byte-32 c)
                                   msg)]
    (swap! token inc)
    (.write out full-msg 0 (+ 4 c))))

;; (declare conn-handler)
;; (declare db)

;; (defn connect [server]
;;   (let [socket (Socket. (:name server) (:port server))
;;         in (DataInputStream. (BufferedInputStream. (.getInputStream socket)))
;;         out (BufferedOutputStream. (.getOutputStream socket))
;;         conn (ref {:in in :out out})]
;;     (doto (Thread. #(conn-handler conn)) (.start))
;;     conn))

;; (defn conn-handler [conn]
;;   (println "Conn handler started")
;;   (while (nil? (:exit @conn))
;;     (let [data (.read (:in @conn))]
;;       (println "let data")
;;       (when data
;;         (println "when data")
;;         (let [pb (protobuf-load Response data)
;;               bpb (bean pb)]
;;           (println "data read in")
;;           (println data)
;;           (println pb)
;;           (println bpb))))))

;; (defn process [data]
;;   (println "Ehm...I got data?")
;;   (println data))

;; (defn conn-handler [conn]
;;   (println "new conn-handler started")
;;   (while (nil? (:exit @conn))
;;     (process (protobuf-seq Response (:in @conn)))))

;; (defn write [conn data]
;;   (protobuf-write (:out @conn) data))

;; (defn write [conn data]
;;   (println (str "outgoing: " data))
;;   (doto (:out @conn)
;;     (.write (gen-ver (.len data)))
;;     (.write data)
;;     (.flush)))

;; (defn fire-away []
;;   (let [db (connect db-host)]
;;     (write db version)
;;     (write db (list-databases))))

(defn get-database-list []
  (let [default {:host "127.0.0.1"
                 :port 28015}
        {:keys [host port]} default]
    (try
      (let [socket (Socket. host port)
            version (to-little-endian-byte-32 magic-number)]
        (with-open [out (DataOutputStream. (.getOutputStream socket))
                    in (DataInputStream. (.getInputStream socket))]
          (swap! token (constantly 1))
          (.write out version 0 4)
          ; (send-term out list-example)
          (map (partial send-term out) list-example)
          (let [data (parse-response in)
                closed (close-connection socket)]
            data)))
      (catch ConnectException e
        (println "Connection refused")))))

(defn -main [& args]
  (let [db-list (get-database-list)
        coerced (coerce-response db-list)]
    (println db-list)
    (println coerced)))
