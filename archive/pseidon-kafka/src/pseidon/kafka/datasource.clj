(ns pseidon.kafka.datasource
  (:require [pseidon.kafka.util :refer [load-datasink load-datasource get-kafka-conf]]
            [pseidon.core.registry :refer [register create-datasource create-datasink] ]))

(let [kconf (get-kafka-conf)]
  (register (load-datasource kconf))
  (register (load-datasink kconf)))
