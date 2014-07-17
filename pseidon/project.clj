(defproject pseidon "2.0.0-SNAPSHOT"
  :description "BigData Import Framework"
  :url "https://github.com/gerritjvv/pseidon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "_"]
                 [thread-exec "_"]
                 [com.taoensso/nippy "_"] 
                 [net.openhft/chronicle "2.1-SNAPSHOT"]
                 [goat "0.1.0-SNAPSHOT"]
                 [robert/hooke "1.3.0"]
                 [riemann-clojure-client "0.2.11"]
                 [commons-net "3.2"]
                 [commons-lang "2.6"]
                 [commons-io "2.4"]
                 [org.apache.commons/commons-vfs2 "2.0"]
                 [org.apache.sshd/sshd-core "0.8.0"]  
                 [com.jcraft/jsch "0.1.50"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [clj-time "_"]
                 [http-kit "2.1.8"]
                 [fun-utils "_"]
                 [compojure "1.1.5"]
                 [cheshire "5.2.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.clojure/tools.cli "0.2.2"]
                 [com.codahale.metrics/metrics-core "3.0.1"]
                 [com.codahale.metrics/metrics-servlets "3.0.1"]
                 [org.xerial.snappy/snappy-java "1.1.1-M1"]
                 [reply "0.1.0-beta9"]
                 [jline "2.11"] ;need for dependency from reply
                 [log4j/log4j "1.2.16" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]
                 [clj-logging-config "1.9.10"]
                 [org.clojure/tools.logging "_"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.apache.curator/curator-framework "2.2.0-incubating"]
                 [org.apache.curator/curator-test "2.2.0-incubating" :scope "test"]
                 
                 [midje "1.6-alpha2" :scope "test"]
                 [org.apache.commons/commons-exec "1.2"]
                 [org.hsqldb/hsqldb "2.3.0"]
                 [org.clojure/java.jdbc "0.3.0-alpha4"]
                 [org.streams/commons "0.2.5" :exclusions [com.google.protobuf/protobuf-java
                                                           org.restlet.jse/org.restlet.ext.jackson
                                                           org.restlet.jse/org.restlet
                                                           com.googlecode.protobuf-java-format/protobuf-java-format
                                                           org.springframework/spring-core
                                                           org.springframework/spring-context
                                                           org.springframework/spring-beans
                                                           cglib/cglib-nodep]]
                 [org.apache.hadoop/hadoop-core "0.20.2" :scope "provided" :exclusions [hsqldb]]
                 [criterium "0.4.1" :scope "test"] ;benchmarking
                 [spyscope "0.1.3" :scope "test"]
                 ]
                
                 
  :aot [pseidon.core pseidon.getenv]
  :main pseidon.core
  :repositories {"sonatype-oss-public"
               "https://oss.sonatype.org/content/groups/public/"
               "streams-repo"
               "https://bigstreams.googlecode.com/svn/mvnrepo/releases"
	       "spring snapshot"
	       "http://repo.springsource.org/libs-snapshot"}
  
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :java-source-paths ["java"]
  :jvm-opts ["-Xmx1g"]
  
  :plugins [
	 [lein-modules "0.3.6"]
         [lein-rpm "0.0.5"] [lein-midje "3.0.1"] [lein-marginalia "0.7.1"] 
         [lein-kibit "0.0.8"] [no-man-is-an-island/lein-eclipse "2.0.0"]
           ]
  :warn-on-reflection true
  
  :rpm {:name "pseidon"
        :summary "pseidon streaming imports"
        :copyright "Apache-2 Licence"
        :workarea "target"
        :mappings [{:directory "/opt/pseidon/lib"
                    :filemode "440"
                    :username "root"
                    :groupname "root"
                    ;; There are also postinstall, preremove and postremove
                    :sources {:source [{:location "target/classes"}
                                       {:location "src"}]
                           }}]}
  )
