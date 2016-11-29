(ns pseidon.view.utils
  (:import  [com.fasterxml.jackson.databind ObjectMapper])
  )

(def objMapper (ObjectMapper.))

(defn write-json [obj]
  (let [writer (java.io.StringWriter.)]
    (.writeValue objMapper writer obj)
    (.toString writer)))

(defn str->int [s]
  (if-not s 0 (Integer/parseInt s)))
