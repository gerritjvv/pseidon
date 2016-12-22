(ns pseidon.view.metrics
  (:require 
            [pseidon.core.metrics :refer [list-metrics run-health-checks]]
            [pseidon.view.utils :refer [write-json]]
            )
  )

(defn metrics-index [req]
  (write-json [ (list-metrics) (run-health-checks) ] ))
