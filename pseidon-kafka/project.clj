(defproject pseidon-kafka "2.0.4-SNAPSHOT"
  :description "FIXME: write description"
  :url "Pseidon kafka plugin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [
                 [kafka-clj "2.3.6-SNAPSHOT" :exclusions [com.taoensso/nippy
                                                          org.clojure/clojure
                                                          org.clojure/tools.reader
                                                          commons-codec]]
                 [midje "1.6-alpha2" :scope "test" :exclusions [joda-time
                                                                org.clojure/tools.namespace]]
                 [pseidon "_" :scope "provided" :exclusions [
                                                             org.clojure/core.async
                                                             com.fasterxml.jackson.core/jackson-core
                                                             com.fasterxml.jackson.core/jackson-databind
                                                             commons-httpclient
                                                             clj-http]]
                 [org.apache.commons/commons-pool2 "2.2"]
                 [commons-pool/commons-pool "1.6"]
                 [clj-tuple "0.1.6"]
                 [org.clojure/clojure "_" :scope "provided"]
                 [reply "0.1.0-beta9" :scope "provided"]
                 [jline "2.11" :scope "provided"] ;need for dependency from reply
                 ]

  :warn-on-reflection true
  
  :plugins [
	  [lein-modules "0.3.6"]
          [lein-rpm "0.0.5"] [lein-midje "3.0.1"] [lein-marginalia "0.7.1"]
          [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
           ]

   :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
   :java-source-paths ["java"]
  
  )
