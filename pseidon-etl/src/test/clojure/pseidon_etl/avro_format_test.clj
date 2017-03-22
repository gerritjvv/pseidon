(ns pseidon-etl.avro-format-test

  (:require [clojure.test :refer :all]
            [pseidon-etl.formats :as formats]
            [pseidon-etl.avro.avro-format :as avro-format]
            [pseidon-etl.writer :as writer]
            [pseidon-etl.test-utils :as test-utils]
            [com.stuartsierra.component :as component])
  (:import (io.confluent.kafka.schemaregistry.client SchemaRegistryClient MockSchemaRegistryClient)
           (org.apache.avro Schema$Parser Schema)
           (io.confluent.kafka.serializers KafkaAvroSerializer KafkaAvroDeserializer)
           (org.apache.avro.generic GenericRecord GenericData$Record IndexedRecord)))


(defonce TEST-TOPIC "TEST")

(defonce TEST-SCHEMA-STR "{\"type\":\"record\",\"name\":\"encryption_output\",\"fields\":[{\"name\":\"timestamp\",\"type\":\"long\"},{\"name\":\"line\",\"type\":\"string\"}]}")

(defn ^Schema schema [json] (.parse (Schema$Parser.) (str json)))

(defn ^SchemaRegistryClient registry-client []
  (MockSchemaRegistryClient.))

(defn ^IndexedRecord record [^Schema schema]
  (GenericData$Record. schema))

(defn test-record [sc v]
  (let [^IndexedRecord r (record sc)]
    (.put r 0 (System/currentTimeMillis))
    (.put r 1 (str v))
    r))


(deftest test-avro-formats-writer
  (let [registry-client (registry-client)

        sleep-ms 500
        sc (schema TEST-SCHEMA-STR)
        format (formats/format-descriptor
                 (atom {:client-reg (delay registry-client)})
                 {}
                 (formats/parse-format "avro-txt:ts=0;msg=1"))

        writer-service (test-utils/test-writer-service sleep-ms)


        total 1000
        msgs (mapv str (range total))]

    (doseq [msg msgs]
      (let [
            record (test-record sc msg)
            bts ((:encoder format) TEST-TOPIC record)
            format-msg (formats/bts->msg {} TEST-TOPIC format bts)
            ]
        (writer/multi-write (:writer-ctx writer-service) (writer/wrap-msg TEST-TOPIC format-msg))))

    (let [msgs-read (test-utils/retry
                      sleep-ms
                      (* 5 60000)
                      #(let [msgs (test-utils/read-msgs (:base-dir writer-service))]
                         (if (= (count msgs) total) msgs nil)))]

      (is (= (count msgs-read) total))
      (is (= (into [] msgs-read) msgs)))

    (component/stop writer-service)))