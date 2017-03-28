(ns
  ^{:doc "Implements avro serde with avro registry support from confluent
          https://github.com/confluentinc/schema-registry


          Format avro-txt  this format is for avro wrapping a txt message
            All messages must be deserializable to IndexedRecord
            props => ts=index the index at which the timestamp in milliseconds can be found, -1 == current time in millis
                  => msg=index the index at which the message can be found, -1 means use whole IndexedRecord
         "}
  pseidon-etl.avro.avro-format
  (:require [pseidon-etl.formats :as formats]
            [clojure.tools.logging :refer [info]])
  (:import (io.confluent.kafka.schemaregistry.client CachedSchemaRegistryClient SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroSerializer KafkaAvroDeserializer)
           (org.apache.avro.generic IndexedRecord)
           (org.apache.commons.lang3 StringUtils)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; constants

(defonce ^Long REGISTRY-CLIENT-MAX-ENTRIES 1000)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;; private functions

(defn encoder [^SchemaRegistryClient client]
  (let [ser (KafkaAvroSerializer. client)]
    (fn [topic record]
      (.serialize ser (str topic) record))))

(defn decoder [^SchemaRegistryClient client]
  (let [des (KafkaAvroDeserializer. client)]
    (fn [topic record]
      (.deserialize des (str topic) (bytes record)))))

(defn format-url [^String url]
  (let [url1 (if (.startsWith url "http") url (str "http://" url))
        url2 (if (.contains url ":") url1 (str url1 ":8081"))]
    url2))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;; formats multi method impls

(defn create-client-reg ^SchemaRegistryClient [conf]
  (let [url (:avro-schema-registry-url conf "localhost")
        max-entries (:avro-schema-registry-max-schemas conf REGISTRY-CLIENT-MAX-ENTRIES)]
    (info "Creating Avro SchemaRegistryClient with " url " identityMapCapacity " max-entries)

    (CachedSchemaRegistryClient. (str (format-url url))  (int max-entries))))

(defmethod formats/format-descriptor "avro-txt" [state-a conf format]

  (let [
        ;;; share the same client-registry via global state
        client-reg (if-let [client-reg (:client-reg @state-a)]
                     @client-reg
                     @(:client-reg
                        (swap! state-a (fn [m]
                                         (if (:client-reg m)
                                           m
                                           (assoc m
                                             :client-reg (delay (create-client-reg conf))))))))

        ;;; use a unique encoder/decoder per log, these must still be threadsafe
        encoder (encoder client-reg)
        decoder (decoder client-reg)

        props (:props format)

        ts-parser (formats/ts-parser props)

        msg-index (get (:props format) "msg")]

    (when (not (StringUtils/isNumeric (str msg-index)))
      (throw (RuntimeException. (str "Avro format descriptor must contain ts and msg as int values e.g avro:ts=0;msg=1"))))

    (assoc format
      :client-reg client-reg
      :encoder encoder
      :decoder decoder
      :ts-parser ts-parser
      :msg-index (Integer/parseInt (str msg-index)))))


(defmethod formats/msg->string "avro-txt" [conf topic format format-msg]
  (str (:msg format-msg)))

;;; NOTE: the msg is expected to always be a string
(defmethod formats/msg->bts "avro-txt" [conf topic format format-msg]
  (.getBytes (str (:msg format-msg)) "UTF-8"))

(defmethod formats/bts->msg "avro-txt" [conf topic format ^"[B" bts]
  (let [ts-parser (:ts-parser format)
        msg-index (int (:msg-index format))

        ^IndexedRecord record ((:decoder format) topic bts)

        ts  (ts-parser record)
        msg (if (> msg-index -1) (.get record msg-index) record)]

    (formats/->FormatMsg format ts bts msg)))