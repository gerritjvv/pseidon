(ns pseidon.test.hdfs.core.hdfsprocessor_test
  (:use [pseidon.hdfs.core.hdfsprocessor]
        [midje.sweet]
        [pseidon.core.conf]
        [pseidon.core.datastore]
        )
  
          
   (:import (org.apache.commons.lang StringUtils)
            (java.io File)
            (org.apache.hadoop.fs Path FileUtil FileSystem)
            (org.apache.hadoop.conf Configuration)
           )
  )
