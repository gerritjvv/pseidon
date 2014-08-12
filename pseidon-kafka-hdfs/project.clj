(defproject pseidon-kafka-hdfs "2.0.1-SNAPSHOT"
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
                 [pseidon "_" :scope "provided"]
                 [mysql/mysql-connector-java "5.1.27"]
                 [org.clojure/java.jdbc "0.3.0-alpha4"]
                 [org.tobereplaced/jdbc-pool "0.1.0"]                
                 [fileape "_"]
                 [com.taoensso/nippy "2.5.2"] 
                 [net.minidev/json-smart "1.2"]
                 [clj-json "0.5.3"]
                 [io.fastjson/boon "0.18"]
                 [nf.fr.eraasoft/objectpool "1.1.2"]
                 [org.clojure/clojure "_"]
                 [midje "1.6-alpha2" :scope "test"]]

  )
