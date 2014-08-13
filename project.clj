(defproject pseidon-suite "_"
  :plugins [[lein-modules "0.3.6"]]

  :profiles {:provided
               {:dependencies [[org.clojure/clojure "_"]
                              ]}
             :dev
               {:dependencies [[midje "_"]]}

             :fast
               {:modules {:subprocess false}}}

  :modules  {:subprocess false
	     :inherited 
               {:deploy-repositories
                              [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]
                :repositories [
				["sonatype-oss-public"
               			 "https://oss.sonatype.org/content/groups/public/"]
               			["streams-repo"
               			 "https://bigstreams.googlecode.com/svn/mvnrepo/releases"]
               			["spring snapshot"
               			 "http://repo.springsource.org/libs-snapshot"]]

                :aliases      {"all" ^:displace ["do" "clean," "test," "install"]
                               "-f" ["with-profile" "+fast"]}}
                :url          "github.com/gerritjvv/pseidon"
                :license      {:name "Apache Software License - v 2.0"
                               :url "http://www.apache.org/licenses/LICENSE-2.0"}

             :versions {
		        pseidon			      "2.0.1-SNAPSHOT"
			pseidon-suite                 "2.0.0-SNAPSHOT"
                        pseidon-hdfs                  "2.0.0-SNAPSHOT"
			pseidon-version               "2.0.0-SNAPSHOT"
			org.clojure/clojure           "1.6.0"
			org.clojure:clojure	      "1.6.0"
                        leiningen-core                "2.3.4"
                        midje                         "1.6.0"
			thread-exec		      "0.2.0-SNAPSHOT"
			com.taoensso/nippy	      "2.4.1"
			clj-time 		      "0.5.1"
			org.clojure/tools.logging     "0.2.3"
		        fun-utils		      "0.4.7"
			fileape			      "0.6.7"
			}})
