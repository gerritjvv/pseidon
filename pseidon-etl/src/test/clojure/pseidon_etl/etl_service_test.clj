(ns pseidon-etl.etl-service-test
    (:require
      [midje.sweet :refer :all]
      [clojure.test :refer :all]
      [conjure.core :refer :all]
      [com.stuartsierra.component :as component]
      [clojure.tools.logging :refer [info error]]
      [clojure.java.io :as io]
      [pseidon-etl.writer :refer [writer-service]]
      [pseidon-etl.topic-service :refer [create-topic-service]]
      [kafka-clj.consumer.node :refer [create-kafka-node-service read-msg!]]
      [pseidon-etl.test-utils :refer [startup-resources shutdown-resources create-test-kafka-node-service create-test-kafka-client-service create-db-tables test-db-service]]
      [pseidon-etl.etl-service :refer [create-etl-service]]
      [kafka-clj.client :refer [send-msg]]
      [pseidon-etl.convert :refer [msg->json json->bts]]
      [pseidon-etl.conf :as conf]
      [pseidon-etl.db-service :refer [with-connection]]
      [clojure.java.jdbc :as j]))


;
;(defonce state-ref (ref {}))
;(defonce system (atom nil))
;
;;(defonce test-msg {"data" [(msg->json (slurp "testmessages"))]})
;
;(defonce topic "mytests")
;
;
;(defn send-test-messages [system topic n]
;  ;(let [c (get-in system [:kafka-client :client])]
;  ;  (dotimes [_ n]
;  ;    (send-msg c topic (json->bts test-msg))))
;  ;
;  )
;
;(defn- load-resources []
;  (let [resources (startup-resources topic)]
;    (dosync (alter state-ref assoc :resources resources))
;    resources))
;
;(defn stop-all []
;  (shutdown-resources (-> state-ref deref :resources)))
;
;(defn insert-db-test-topics
;  "Add the test topic to the correct db tables so that the etl-service will read it"
;  [db-service]
;  (with-connection db-service
;                   (j/insert-values
;                     :pseidon_logs
;                     [:log :type :log_group :enabled]
;                     [topic "etl" "etl" 1])
;                   (j/insert-values
;                     :kafka_formats
;                     [:log :format :output_format :hive_table_name :hive_url :hive_user :hive_pwd]
;                     [topic "json" "json" "NA" "NA" "NA" "NA"])))
;
;(defn start-components [data-dir]
;  (let [db-service (test-db-service)
;
;        conf       {:data-dir data-dir
;                    :writer   {:codec :r1-parquet
;                               :check-freq           500
;                               :rollover-size        100000
;                               :rollover-abs-timeout 10000
;                               :parallel-files       2
;                               :rollover-timeout     10000}}]
;    (load-resources)
;    (create-db-tables db-service)
;    (insert-db-test-topics db-service)
;
;    (component/start
;      (component/system-map
;        :db db-service
;        :kafka-client (create-test-kafka-client-service (-> state-ref deref :resources))
;        ;create the kafka-node with the topic we need so that there is no need to have the topic updater service running
;        :kafka-node (create-test-kafka-node-service (-> state-ref deref :resources) [topic])
;        :writer-service (writer-service conf)
;        :topic-service (component/using
;                         (create-topic-service {:topic-refresh-freq-ms 500 :etl-group "etl"})
;                         {:database       :db
;                          :kafka-node     :kafka-node
;                          :writer-service :writer-service})
;        :etl-service (component/using
;                       (create-etl-service (merge (binding [conf/*default-conf* (conf/load-conf-m "src/main/resources/pseidon.edn")]
;                                                           (conf/conf))
;                                                  conf))
;                       ;topic-service kafka-node kafka-client writer-service
;                       [:db :topic-service :kafka-node :kafka-client :writer-service])))))
;
;(defn start
;  [data-dir]
;  (start-components data-dir))
;
;(defn stop-components [s]
;  (try
;    (component/stop s)
;    (catch Exception e (error e e)))
;  (stop-all))
;
;(defn stop [s]
; )
;
;(defonce message-count 100000)
;
;(def data-dir (doto (io/file (str "target/test/datadirs/" (System/currentTimeMillis)))
;                .mkdirs))
;
;(deftest run-etl-service
;  (with-state-changes [(before :facts (reset! system (start data-dir)))
;
;                       (after :facts (if @system (stop @system)))
;                       ]
;
;                      (fact "Test run etl service"
;                            (send-test-messages @system topic message-count)
;                            (info "waiting on first etl message")
;                            ;here we need to wait for files written
;                            (Thread/sleep 10000))))
