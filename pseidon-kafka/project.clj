(defproject pseidon-kafka "2.0.2-SNAPSHOT"
  :description "FIXME: write description"
  :url "Pseidon kafka plugin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [
                 [org.clojure/core.async "LATEST"]
                 [kafka-clj "2.3.0"]
                 [kafka-events-disk "0.2.2-SNAPSHOT"]
                 [midje "1.6-alpha2" :scope "test"]
                 [pseidon "_" :scope "provided"]
                 [com.taoensso/nippy "2.5.2"]
                 [night-vision "0.1.0-SNAPSHOT" :scope "test"]
                 [net.sf.jopt-simple/jopt-simple "3.2" :scope "provided"]
                 [clj-tuple "0.1.4"]
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

;   :aot [pseidon.kafka.core]
;   :main pseidon.kafka.core
   :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
   :java-source-paths ["java"]
  
  )
