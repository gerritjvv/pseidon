(ns pseidon.view.registry
  (:require 
            [pseidon.core.registry :refer [reg-list-all]]
            [pseidon.view.utils :refer [write-json]]
            )
  )


(defn registry-index [req]
  (write-json (reg-list-all))
  )