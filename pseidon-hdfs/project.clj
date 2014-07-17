(defproject pseidon-hdfs "2.0.0-SNAPSHOT"
  :description "A pseidon data source, channel and processor for writing to hdfs"
  :url "github.com/gerritjvv/pseidon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}


  :dependencies [

                  [org.clojure/clojure "_" :scope "provided"]
                  [org.apache.hadoop/hadoop-core "1.2.1" :scope "provided"
		                    :exclusions [hsqldb]]
                  [org.apache.hadoop/hadoop-test "1.2.1" :scope "test"]
                  [pseidon "_" :scope "provided"]
                  [commons-lang "2.6" :scope "provided"]
                  [midje "1.6-alpha2" :scope "test"]
                  [org.clojure/data.json "0.2.3"]
                  ]
  :repositories {
		 "cloudera"
 		 "https://repository.cloudera.com/artifactory/cloudera-repos/"
	         "sonatype-oss-public" 
		 "https://oss.sonatype.org/content/groups/public/"
          	}
  
  
  :plugins [
		[lein-modules "0.3.6"]
            [lein-midje "3.0.1"] 
            [lein-marginalia "0.7.1"] 
            [lein-kibit "0.0.8"]
           ]
  :warn-on-reflection true
  
		)


