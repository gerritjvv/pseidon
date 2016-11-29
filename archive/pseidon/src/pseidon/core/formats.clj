(ns pseidon.core.formats
    "Provides a standard object to bytes and bytes to object tranform for all plugins
     Uses https://github.com/cognitect/transit-clj
     The default :msgpack is used"
    (:require [cognitect.transit :as transit])
    (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn to-bytes
      "Serializes the object into bytes"
      [m]
      (let [out (ByteArrayOutputStream. 50)
            writer (transit/writer out :msgpack)]
           (transit/write writer m)
           (.toByteArray out)))

(defn to-obj
      "Read bytes serialized by to-bytes and returns the object that was originally written"
      [^"[B" bts]
      (let [in (ByteArrayInputStream. bts)
            reader (transit/reader in :msgpack)]
           (transit/read reader)))
