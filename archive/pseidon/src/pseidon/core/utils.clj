(ns pseidon.core.utils
  (:require [clojure.core.async :refer [go timeout <!]]
            [clojure.tools.logging :refer [info]]
           )
  (:import (clojure.lang ArityException))
  )

(defn not-interrupted []
  (not (Thread/interrupted)))

(defn merge-distinct-vects[v1 v2]
  "
   Merge the two vectors with only distinct elements remaining
  "
  (if (empty? v2)
    v1 
    (-> (apply conj v1 v2) set vec)))

(defn buffered-select [f-select init-pos]
  "Creates a lazy sequence of messages for this datasource"
  (letfn [  
           (m-seq [buff pos] 
                   (let [buff2 (if (empty? buff) (f-select pos) buff)]
                         (cons (first buff2) (lazy-seq (m-seq (rest buff2) (inc pos) )))         
                     )
                   )
           ]
    (m-seq nil init-pos)
    )
  )


(defmacro fixdelay [ ms & body]
  "Runs the body every ms after the last appication of body completed"
          `(go (loop [] (<! (timeout ~ms)) ~@body (recur))))
  

(defn apply-f [f arg]
  "Helper function that applies first (f arg) and if an ArityException was thrown, applies (f)"
  (try (f arg)
    (catch ArityException e (f)))
  )