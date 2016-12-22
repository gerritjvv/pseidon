(ns pseidon.view.datastore
  (:require [pseidon.core.datastore :refer [list-dirs set-data! get-data-str delete!]]
            [clojure.tools.logging :refer [info ]]
            [pseidon.view.utils :refer [write-json]])
  (:import [org.apache.commons.lang StringUtils]))

"A datastore browser that can read and set values, values set set via query strings"

(defn iterate-dirs [^String parent ^Long level ^String d]
  (let [dirs (list-dirs parent d)]
    ;(info "iterate parent " parent " d " d " " (type d) " " level " dirs " dirs)
    (if (and (not (empty? dirs)) (> level 0))
	    {d  (into {} (pmap (partial iterate-dirs (str parent "/" d) (dec level)) dirs))
          ;(mapcat (partial iterate-dirs (str parent "/" d) (dec level)) dirs ))
      }
	    {d (get-data-str parent d)} )))

(defn datastore-list [{:keys [query-params]}]
      
      (let [path (get query-params "path" "/pseidon")
           depth (Integer/parseInt (get query-params "d" "2"))
           ]
        
        (write-json (iterate-dirs "/" depth path))))
       
(defn datastore-delete [{:keys [query-params]}]
    (let [path (get query-params "path" "/pseidon")]
      (if path 
        (delete! path))
      (write-json {:deleted path})))
      

(defn datastore-create [{:keys [query-params]}]
     (let [path (get query-params "path" "/pseidon")
           path-val (get query-params "v" nil)
           t (get query-params "t" nil)
       
			     v (cond (= t "i") (Integer/parseInt path-val)
			              (= t "l") (Long/parseLong path-val)
			              :else
			                   (if path-val
			                            (cond (StringUtils/isNumeric path-val) (Long/parseLong path-val)
			                            :else path-val)
			                            path-val))]
			       
            (set-data! path nil v)
            (info "set data " path "=" (type v))))
   