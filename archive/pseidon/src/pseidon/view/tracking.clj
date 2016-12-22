(ns pseidon.view.tracking
  (:require 
            [pseidon.core.tracking :refer [select-messages with-txn dbspec]]
            [pseidon.view.utils :refer [str->int write-json]]
            [clojure.tools.logging :refer [info error]]
            )
  )

(defn remove-parens [s]
  (.substring (.trim s) 1 (- (count s) 1)) )

(defn clean-str [s]
   (when s
     (if (.startsWith s "\"") (remove-parens s) s)
     ))

(defn tracking-index [{{:keys [q from max] :or {:from 0 :max 100}} :params}]
   (info "tracking-index dbspec " dbspec)
   (write-json 
        (with-txn dbspec (select-messages (clean-str q) (str->int from) (str->int max))))
   )