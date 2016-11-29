(defproject pseidon-kafka-hdfs "2.0.5-SNAPSHOT"
  :description "Pseidon pluging that copies data from kafka to hdfs"
  :url "https://github.com/gerritjvv/pseidon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :warn-on-reflection true

  :plugins [
	  [lein-modules "0.3.6"]
          [lein-rpm "0.0.5"] [lein-midje "3.0.1"] [lein-marginalia "0.7.1"]
          [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
           ]

  :dependencies [
                 [pseidon "_" :scope "provided" :exclusions [
                                                              clj-http
                                                              org.clojure/tools.logging
                                                              com.fasterxml.jackson.core/jackson-core
                                                              commons-httpclient
                                                             ]]
                 [mysql/mysql-connector-java "5.1.27"]
                 [org.clojure/java.jdbc "0.3.0-alpha4"]
                 [org.tobereplaced/jdbc-pool "0.1.0"]                
                 [fileape "_" :exclusions [org.apache.hadoop/hadoop-core
                                           org.clojure/clojure
                                           fun-utils
                                           org.clojure/tools.logging]]
                 [pjson "0.2.2-SNAPSHOT" :exclusions [cheshire]]
                 [com.taoensso/nippy "2.5.2"] 
                 [org.clojure/clojure "_"]
                 [midje "1.6-alpha2" :scope "test" :exclusions [clj-time]]])
