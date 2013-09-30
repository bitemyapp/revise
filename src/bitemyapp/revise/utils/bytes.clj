(ns bitemyapp.revise.utils.bytes
  "Byte array shenanigans"
  (:import [java.nio ByteOrder ByteBuffer]))

(defn to-little-endian-32
  [n]
  (let [buf (ByteBuffer/allocate 4)]
    (doto buf
      (.putInt n)
      (.order ByteOrder/LITTLE_ENDIAN))
    (.getInt buf 0)))

(defn to-byte-32
  [n]
  (let [buf (ByteBuffer/allocate 4)]
    (.putInt buf n)
    (.array buf)))

(defn to-little-endian-byte-32
  "(-> n to-little-endian-32 to-byte-32) pretty much"
  [n]
  (let [buf (ByteBuffer/allocate 4)]
    (doto buf
      (.order ByteOrder/LITTLE_ENDIAN)
      (.putInt n))
    (.array buf)))

(defn parse-little-endian-32
  [b]
  (let [buf (ByteBuffer/allocate 4)]
    (doto buf
      (.order ByteOrder/LITTLE_ENDIAN)
      (.put b))
    (.getInt buf 0)))

(defn concat-byte-arrays
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
