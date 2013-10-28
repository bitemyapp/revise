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

;; Clearly not final implementation
;; (defonce current-connection (atom nil))

(defn close
  "Close the connection"
  ([] (when-let [current @current-connection]
        (close current)))
  ([conn]
     (.close (:out conn))
     (.close (:in conn))
     (.close (:socket conn))
     true))

(defn fetch-response
  [^DataInputStream in]
  (let [size (byte-array 4)]
    (.read in size 0 4)
    (let [size (parse-little-endian-32 size)
          resp (byte-array size)]
      (.read in resp 0 size)
      (pb/protobuf-load Response resp))))

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

;; God damn
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

(defn send-protobuf
  "Send a protobuf to the socket's outputstream"
  [^DataOutputStream out ^PersistentProtocolBufferMap data]
  (let [msg (pb/protobuf-dump data)
        c (count msg)
        full-msg (concat-byte-arrays (to-little-endian-byte-32 c)
                                     msg)]
    (.write out full-msg 0 (+ 4 c))))

;; (clojure.core/send-off blah (fn [m _] (update-in m [:a] inc)) nil)

(defn send-term
  [^PersistentProtocolBufferMap term conn]
  (let [type :START
        {:keys [in out last-token]} conn
        token (inc last-token)
        prom (promise)]
      (send-protobuf out (pb/protobuf Query {:query term
                                             :token token
                                             :type type}))
      (swap! current-connection update-in [:token] inc)

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
    ;; (send-version out)
    ;; (send-auth-key out auth-key)
    ;; (println "When connecting:" (read-init-response in))
    conn))

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
