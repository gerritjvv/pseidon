(ns pseidon.core.message
  (:require  [taoensso.nippy :as nippy])
  (:import [pseidon.util Encodable Decoder])
  )

(declare create-message)

;bytes-f returns a byte array Do not create this record directly 
;rather use the methods provided in the ns, you'll be shielded from future changes.
; ids is a sequence of ids as read by the datasource. The datasource should produce the id and also save the ids to the tracking service
; note the ids passed to the Channels from where the Channel creates a message instance.
;bytes-seq is a sequence of byte array items.
(defrecord Message [bytes-seq ^String ds ids ^String topic  accept ^long ts ^long priority] 
  
  Comparable
     (compareTo [this m] 
       (compare (:priority this) (:priority m)))
  Encodable
      (getBytes [this]
        (nippy/freeze [bytes-seq ds ids topic accept ts priority]))
  )

(def MESSAGE-DECODER (reify 
                       Decoder
                       (decode [this ^bytes bts]
                         (let [ [bytes-seq ds ids topic accept ts priority] (nippy/thaw bts)]
                           (create-message bytes-seq ds ids topic accept ts priority))))) 

(defn change-bytes [^Message m ^clojure.lang.IFn bytes-f]
  (->Message  bytes-f (:ds m) (:ids m) (:topic m) (:accept m) (:ts m) (:priority m))
  )

(defn get-ids [{:keys [ids]}] 
  (if (instance? java.util.Collection ids) ids [ids])  
  )

(defn create-message [bytes-f ds ids topic accept ts priority]
  (->Message bytes-f ds ids topic accept ts priority)
  )

(defn get-bytes-seq [{:keys [bytes-seq] }]
  "
   Bytes can be a function or the direct byte array,
   This makes reading bytes at the correct moment more flexible
   "
   (cond (instance? clojure.lang.IFn bytes-seq) (get-bytes-seq (bytes-seq))
         (or (seq? bytes-seq) (vector? bytes-seq)) bytes-seq
         :else [bytes-seq])
  )


(defn batched-seq [rdr size] 
  "
     Reads a batch of len=size records from rdr, if rdr returns nil the batch size may be smaller than size.
    "
    (partition-all size (take-while (complement nil?) rdr))
     )

