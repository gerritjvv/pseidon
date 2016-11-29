(ns pseidon-kafka-hdfs.json-csv-test
  (:use [pseidon.kafka-hdfs.json-csv]
        [midje.sweet]))

(facts "Json to csv convert"
  (fact "Should parse column definitions"
   (parse-col-def ["/a/b/c" 1]) => [["a" "b" "c"] 1]
   (parse-col-def ["////a/b/c" 1]) => [["a" "b" "c"] 1])
  
  (fact "Should parse definitions"
    (parse-definitions "[[\"a/b\", 1],[\"c/d\", 2],[\"e/f/g\", 3]]") => [[["a" "b"] 1] [["c" "d"] 2] [["e" "f" "g"] 3]])
  
  (fact "Should convert to array"
    (let [v (parse-definitions "[[\"a/b\", 1],[\"c/d\", 2],[\"e/f/g\", 3]]")]
     (json->array v {}) => '(1 2 3)
     (json->array v {"a" {"b" "p"} "c" {"d" "q"} "e" {"f" {"g" "r"}}}) => '("p" "q" "r")))
  
  (fact "Should convert to csv string"
    (let [v (parse-definitions "[[\"a/b\", 1],[\"c/d\", 2],[\"e/f/g\", 3]]")]
     (json->csv v "," {}) => "1,2,3")))
  
    
