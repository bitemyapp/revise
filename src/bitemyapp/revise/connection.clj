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
            [bitemyapp.revise.response :refer [inflate]]))

(defn close
  "Close the connection"
  ([] (when-let [current @current-connection]
        (close current)))
  ([conn]
     (.close (:out conn))
     (.close (:in conn))
     (.close (:socket conn))
     true))

(defn send-number
  [^DataOutputStream out n]
  (.write out (to-little-endian-byte-32 n) 0 4))

(defn send-version
  "Send the version. First step when making a connection"
  [^DataOutputStream out]
  ;; fking cant figure out how to get these out of the damn protobuffer
  (let [v1 1063369270
        v2 1915781601]
    (send-number out v2)))

(defn send-auth-key
  [^DataOutputStream out auth-key]
  (let [c (count auth-key)]
    (send-number out c)
    (.writeChars out auth-key)))

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
        prom (get (:waiting conn) token)]
    ;; (println "conn: " conn)
    ;; (println "result: " result)
    ;; (println "prom: " prom)
    (deliver prom result)
    (dissoc-in conn [:waiting token])))

(defn read-into-conn [conn-agent]
  ;; (println "reader started")
  (while (not (:inputShutdown (bean (@conn-agent :socket))))
    ;; (spit "out.log" "ran read-into-conn")
    (let [resp (fetch-response (@conn-agent :in))]
      (send-off conn-agent deliver-result (inflate resp)))))

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
        conn (agent {:socket  socket
                     :token   token
                     :waiting {}
                     :out     out
                     :in      in})]
    (send-version out)
    (send-auth-key out auth-key)
    (assert (= (read-init-response in) "SUCCESS"))
    ;; Dunno if this is kosher.
    (future (read-into-conn conn))
    conn))

(defn send-protobuf
  "Send a protobuf to the socket's outputstream"
  [^DataOutputStream out ^PersistentProtocolBufferMap data]
  (let [msg (pb/protobuf-dump data)
        c (count msg)
        full-msg (concat-byte-arrays (to-little-endian-byte-32 c)
                                     msg)]
    (.write out full-msg 0 (+ 4 c))))

(defn send-bump-token [conn prom ^PersistentProtocolBufferMap term]
  (let [{:keys [in out token]} conn
        type :START]
    (send-protobuf out (pb/protobuf Query {:query term
                                           :token token
                                           :type type}))
    ;; (println conn)
    ;; (println "sent protobuf have new-token: " new-token)
    ;; (println "inc'd: " (inc (:token conn)))
    (-> (update-in conn [:token] inc)
        (assoc-in [:waiting token] prom))))

(defn send-term
  [^PersistentProtocolBufferMap term conn]
  (let [prom (promise)]
    (send-off conn send-bump-token prom term)
    prom))

;; Clearly not final implementation
;; (defonce current-connection (atom nil))
;; (declare current-connection) ; Just so it compiles for now

;; (clojure.core/send-off blah (fn [m _] (update-in m [:a] inc)) nil)

;; (defn send
;;   "Send a start query to the current connection, assume everything's open."
;;   [^PersistentProtocolBufferMap term]
;;   (if-let [current @current-connection]
;;     (let [type :START
;;           token (inc (:token current))
;;           {:keys [in out]} current]
;;       (send-protobuf out (pb/protobuf Query {:query term
;;                                              :token token
;;                                              :type type}))
;;       (swap! current-connection update-in [:token] inc)
;;       (let [r (fetch-response in)]
;;         (inflate r)))))

;; (defn connect
;;   [& [conn-map]]
;;   (let [default {:host "127.0.0.1"
;;                  :port 28015
;;                  :token 0
;;                  :auth-key ""}
;;         conn-map (merge default conn-map)
;;         current @current-connection]
;;     (when current
;;       (close current))
;;     (try
;;       (let [token (:token conn-map)
;;             auth-key (:auth-key conn-map)
;;             socket (Socket. (:host conn-map) (:port conn-map))
;;             out (DataOutputStream. (.getOutputStream socket))
;;             in  (DataInputStream. (.getInputStream socket))
;;             conn (reset! current-connection
;;                          {:socket socket
;;                           :token token
;;                           ;; Data*Streams to send/receive bytes
;;                           :out out
;;                           :in in})]
;;         (send-version out)
;;         ;; todo
;;         (send-auth-key out auth-key)
;;         (assert (= (read-init-response in) "SUCCESS"))
;;         conn)
;;       (catch ConnectException e
;;         (println "Couldn't connect to" (str (:host conn-map)
;;                                             ":"
;;                                             (:port conn-map))
;;                  (.getMessage e))))))
