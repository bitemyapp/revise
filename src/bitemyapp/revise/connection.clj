(ns bitemyapp.revise.connection
  "Connection shenanigans"
  (:refer-clojure :exclude [send])
  (:import [java.io DataInputStream DataOutputStream]
           [java.net Socket ConnectException]
           [flatland.protobuf PersistentProtocolBufferMap])
  (:require [flatland.protobuf.core :as pb]
            [bitemyapp.revise.utils.bytes :refer [to-little-endian-byte-32
                                                  parse-little-endian-32
                                                  concat-byte-arrays]]
            [bitemyapp.revise.protodefs :refer [Query Response]]
            [bitemyapp.revise.response :refer [inflate]]
            [clojure.core.async :as async :refer [chan <!! >!!]]
            [clojure.core.async.impl.protocols :as async-impl]))

(defn close
  "Close the connection"
  ([conn]
     (let [c      @conn
           out    (:out c)
           in     (:in c)
           socket (:socket c)
           reader-signal (:reader-signal c)]
       (deliver reader-signal :stop)
       (.close out)
       (.close in)
       (.close socket)
       true)))

(defn send-number
  [^DataOutputStream out n]
  (.write out (to-little-endian-byte-32 n) 0 4))

(defn send-version
  "Send the version. First step when making a connection"
  [^DataOutputStream out]
  ;; fking cant figure out how to get these out of the damn protobuffer
  (let [v1 1063369270
        v2 1915781601
        ;v3 1601562686  ;; Not implemented yet in rdb 1.12
        ]
    (send-number out v2)))

(defn send-auth-key
  [^DataOutputStream out auth-key]
  (let [c (count auth-key)]
    (send-number out c)
    (.writeChars out auth-key)))

;; TODO - not yet available in 1.12
(defn send-protocol-number
  "Not implemented yet as of rdb 1.12"
  [^DataOutputStream out]
  (let [protobuf 656407617
        json 2120839367]
    (send-number out protobuf)))

(defn read-init-response
  [^DataInputStream in]
  (let [s (StringBuilder.)
        x (byte-array 1)]
    (loop [c (.read in x)]
      (let [x1 (aget x 0)]
        (if (== 0 x1)
          (.toString s)
          (do (.append s (char x1))
              (recur (.read in x))))))))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn socket-error? [e]
  (not (nil?
        (re-find #"No method in multimethod 'initial' for dispatch value: null"
                 (:message (bean e))))))

(defn socket-error [cause]
  (Exception. (str "Connection has failed, reconnect to continue. Caused by: " cause)))

(defn fetch-response
  [^DataInputStream in]
  (let [size (byte-array 4)]
    (.read in size 0 4)
    (let [size (parse-little-endian-32 size)
          resp (byte-array size)]
      (.read in resp 0 size)
      (pb/protobuf-load Response resp))))

(defn deliver-result [conn result]
  (let [token (:token result)
        channel (get (:waiting conn) token)
        success-partial (and (= :success (:type result))
                             (= :success-partial (:success result)))]
    (>!! channel result)
    (if success-partial ;; response is incomplete
      conn
      (do
        (async/close! channel)
        (dissoc-in conn [:waiting token])))))

(defn fail-with-error [conn error]
  (let [channels (vals (:waiting conn))]
    (doseq [c channels]
      (>!! c error)
      (async/close! c))
    conn))

(defn read-into-conn [conn reader-signal]
  (while (not (or (agent-error conn)
                  (and (= (when (realized? reader-signal) @reader-signal) :stop)
                       (:inputShutdown (bean (@conn :socket))))))
    (try
      (let [resp (fetch-response (@conn :in))]
        (when (= resp {})
          (throw (socket-error "deserialized response was {}.")))
        (send-off conn deliver-result (inflate resp)))
      (catch Exception e
          (send-off conn fail-with-error e)))))

(defn connect
  [& [conn-map]]
  (let [default {:host "127.0.0.1"
                 :port 28015
                 :token 0
                 :auth-key ""}
        conn-map (merge default conn-map)
        token (:token conn-map)
        auth-key (:auth-key conn-map)
        socket (Socket. (:host conn-map) (:port conn-map))
        out (DataOutputStream. (.getOutputStream socket))
        in  (DataInputStream. (.getInputStream socket))
        reader-signal (promise)
        conn (agent {:socket  socket
                     :token   token
                     :reader-signal reader-signal
                     :waiting {}
                     :out     out
                     :in      in})]
    (send-version out)
    (send-auth-key out auth-key)
    (assert (= (read-init-response in) "SUCCESS"))
    ;; Dunno if this is kosher.
    (future (read-into-conn conn reader-signal))
    conn))

(defn protobuf-send
  "Send a protobuf to the socket's outputstream"
  [^DataOutputStream out ^PersistentProtocolBufferMap data]
  (let [msg (pb/protobuf-dump data)
        c (count msg)
        full-msg (concat-byte-arrays (to-little-endian-byte-32 c)
                                     msg)]
    (.write out full-msg 0 (+ 4 c))))

(defn protobuf-send-start
  [conn channel ^PersistentProtocolBufferMap term]
  (let [{:keys [out token]} conn
        type :START]
    (try
      (protobuf-send out (pb/protobuf Query {:query term
                                             :token token
                                             :type type}))
      (catch Exception e
        (send-off *agent* fail-with-error e)))
    (-> (update-in conn [:token] inc)
        (assoc-in [:waiting token] channel))))

(defn protobuf-send-continue
  [conn token]
  (let [{:keys [out]} conn
        type :CONTINUE]
    (try
      (protobuf-send out (pb/protobuf Query {:token token
                                             :type type}))
      (catch Exception e
        (send-off *agent* fail-with-error e)))
    conn))

(defn protobuf-send-stop
  [conn token]
  (let [{:keys [out]} conn
        type :CONTINUE]
    (try
      (protobuf-send out (pb/protobuf Query {:token token
                                             :type type}))
      (catch Exception e
        (send-off *agent* fail-with-error e)))
    conn))

(defn protobuf-send-noreply-wait
  [conn channel]
  (let [{:keys [out token]} conn
        type :NOREPLY_WAIT]
    (try
      (protobuf-send out (pb/protobuf Query {:token token
                                             :type type}))
      (catch Exception e
        (send-off *agent* fail-with-error e)))
    (-> (update-in conn [:token] inc)
        (assoc-in [:waiting token] channel))))

(defn send-start
  [^PersistentProtocolBufferMap term conn]
  (let [c (chan)]
    (send-off conn protobuf-send-start c term)
    c))

(defn send-continue
  [token conn]
  (when-let [c (get-in @conn [:waiting token])]
    (if (async-impl/closed? c)
      (send-off conn fail-with-error
                (Exception. (str "The 'waiting' channel for token '" token
                                 "' was closed prematurely!")))
      (do
        (send-off conn protobuf-send-continue token)
        c))))

(defn send-stop
  [token conn]
  (when-let [c (get-in @conn [:waiting token])]
    (if (async-impl/closed? c)
      (send-off conn fail-with-error
                (Exception. (str "The 'waiting' channel for token '"
                                 token "' was closed prematurely!")))
      (do
        (send-off conn protobuf-send-stop token)
        c))))

(defn send-wait
  [conn]
  (let [c (chan)]
    (send-off conn protobuf-send-noreply-wait c)
    c))
