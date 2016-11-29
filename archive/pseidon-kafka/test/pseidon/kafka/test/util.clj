(ns pseidon.kafka.test.util
  
  (:import [org.apache.zookeeper.server NIOServerCnxn ZooKeeperServer NIOServerCnxnFactory]
           [org.apache.zookeeper.server.persistence FileTxnSnapLog]
           [java.io File FileInputStream]
           [java.net InetSocketAddress]
           [java.util Properties]
           [kafka.server KafkaServer KafkaConfig]
           [kafka.utils SystemTime$])
  
  )


(defn zookeeper []
  (let [tmp-file (File. (str "target/testConsumer/" (System/currentTimeMillis)))]
    (.mkdirs tmp-file)
    (let [ftxn (FileTxnSnapLog. tmp-file tmp-file)
          zk-server (doto (ZooKeeperServer.) 
                      (.setTxnLogFactory ftxn)
                      (.setTickTime 200)
                      (.setMinSessionTimeout 1000)
                      (.setMaxSessionTimeout 1000))
          cnx-fact (doto (NIOServerCnxnFactory.) (.configure (InetSocketAddress. 2181) 2181)) ]
      (.startup cnx-fact zk-server)
      zk-server)))

(defn kafka-server []
  ;class KafkaServer(val config: KafkaConfig, time: Time = SystemTime) extends Logging {
  (let [ props (doto (Properties.) (.load (FileInputStream. "test/resources/server.properties"))) ]
    (doto (KafkaServer. (KafkaConfig. props) (SystemTime$/MODULE$)) .startup)))
  
