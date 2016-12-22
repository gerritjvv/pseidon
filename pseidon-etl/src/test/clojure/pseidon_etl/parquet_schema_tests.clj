(ns
  ^{:doc "
            Test the loading and case sensitivity of the parquet schema loading
         "
    }
  pseidon-etl.parquet-schema-tests

  (:require [pseidon-etl.parquet.schema :as schema]
            [clojure.test :refer :all])
  (:use midje.sweet)
  (:import (org.apache.hadoop.hive.serde2.typeinfo StructTypeInfo)))


(facts "Test that all keys are converted to lower case"
       (keys (reduce schema/parse-describe-row {} [{:col_name "MyCol" :data_type "int"} {:col_name "ABC" :data_type "int"}])) => ["mycol", "abc"])


(defn struct-field-names [^StructTypeInfo info]
  (.getAllStructFieldNames info))

(deftest test-all-types-converted-to-lower
  (facts
    (struct-field-names
      (first
        (first
          (vals (reduce schema/parse-describe-row {} [{:col_name "message" :data_type "struct<VGUID:string>"}])))))
    => ["vguid"]))

