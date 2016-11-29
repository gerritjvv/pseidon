(ns pseidon.kafka-hdfs.json-csv
  (:require [clj-json [core :as json]]
             [clojure.tools.logging :refer [info error]]))

;converts json maps to csv based on a definition

;definition grammer
;[["path in map a/b/c" "default"], ... ]
; path defines where in the map does the value appear, default is the value to be used if no value is found in the map
; if the path is empty an empty string is inserted

(defn- remove-empty-vals 
  "Helper function that removes any empty strings in a vector, this may happen with string split"
  [v]
  (reduce (fn [state i] (if (not-empty i) (conj state i) state)) [] v))

(defn- check-empty 
  "If v is empty [-1] is returned, this will allow get-in to return the default value rather than the whole map"
  [v] (if (empty? v) [-1] v))

(defn parse-col-def 
  "Converts each [path val] item to a [path-vec val] value, to be used by parse-definitions"
  [[path-str def-val]]
  [(remove-empty-vals (check-empty (clojure.string/split path-str #"/"))) def-val])

(defn parse-definitions 
  "Parses the column definitions, the results of this function should be passed to json->csv
   s can be a string or a vector of vectors, if a string its parsed as json"
  [s]
    (vec (map parse-col-def (if (string? s) (json/parse-string s) s))))


(defn json->array 
  "parsed-defs: result of parse-definitions
   json-map a map with the json values"
  [parsed-defs json-map]
  (map (fn [[path def-val]] (get-in json-map path def-val)) parsed-defs)) 
  
(defn json->csv 
  "Returns a csv string"
  [parsed-defs sep json-map]
  (->> json-map 
    (json->array parsed-defs)
    (clojure.string/join sep)))
                             