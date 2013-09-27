(defproject pseidon-kafka "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.apache.kafka/kafka_2.9.2 "0.8.0-beta1"]
                 [org.scala-lang/scala-library "2.9.2"]
                 [clj-kafka "0.1.0-0.8-beta1"]
                 [com.yammer.metrics/metrics-core "2.2.0" :scope "test"]
                 [com.github.sgroschupf/zkclient "0.1" :scope "test"]
                 [midje "1.6-alpha2" :scope "test"]
                 [pseidon "0.3.1-SNAPSHOT" :scope "provided"]
                 [org.apache.curator/curator-framework "2.2.0-incubating" :scope "test"]
                 [org.apache.curator/curator-test "2.2.0-incubating" :scope "test"]
                 [night-vision "0.1.0-SNAPSHOT" :scope "test"]
                 [org.apache.zookeeper/zookeeper "3.4.5" :scope "test"]
                 [org.clojure/clojure "1.5.1"]]


  :plugins [
          [lein-rpm "0.0.5"] [lein-midje "3.0.1"] [lein-marginalia "0.7.1"]
          [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
           ]

  )