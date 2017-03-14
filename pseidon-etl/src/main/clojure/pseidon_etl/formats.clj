(ns
  ^{:doc "abstracts the different input formats from kafka
           Logs can be txt, json etc.

           A format descritor is written as type:meta  where meta is a key1=value1;key2=value2;...

           The format descriptor is parsed and sent to the defining multi method using:

             format-descriptor: conf format -> Format ;; here different types can add contextual data. A single Format object is shared
             bts->-msg: conf topic format bts -> (format-msg :ts :bts :msg)
             msg->-string: conf topic format format-msg -> string

             Note that conf is the global config


           Format and state:
            because format is a record, properties can be added to it via assoc.
            state held in the format is on a per log basis

            Glogal state can be maintained via the format-descriptor to which a state:Atom is passed to.

           Format txt
            this is for textual lines which are separated by some character
            props =>  sep=<separator> the separator to split the string value by, default=byte1
                      allowed values: any character or \"byte1\" for byte one, \"tab\", \"space\"
                      ts=<timestamp column index> the index in the message at which the timestamp can be found

                      e.g txt:sep=tab;ts=0  will look for tab separated messages where the timestamp in millis is at index 0

            bts->msg => FormatMsg { ts bts msg:Vector}

           Format avro
             see pseidon-etl.avro/avro-format
          "}
  pseidon-etl.formats
  (:require [clojure.string :as string]
            [clj-time.coerce :as c])
  (:import (java.util Map Date)
           (org.apache.commons.lang3 StringUtils)
           (pseidon_etl FormatMsg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Data Types and multimethods

(defrecord Format [^String type ^Map props])

(defmulti bts->msg (fn [conf topic format bts] (:type format)))

(defmulti msg->string (fn [conf topic format msg] (:type format)))

(defmulti msg->bts (fn [conf topic format msg] (:type format)))

(defmulti format-descriptor (fn [state conf format] (:type format)))

(defmulti dispose-format (fn [state conf format] (:type format)))

(declare parse-format)

(defn ^FormatMsg ->FormatMsg [format ^long ts ^"[B" bts msg]
  (FormatMsg. format ts bts msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;; utility functions

(defonce BYTE1 \u0001)
(defonce TAB \tab)
(defonce SPACE \space)


(defn ts->long ^long [ts]
  (cond
    (number? ts) (long ts)
    (string? ts) (if (StringUtils/isNumeric (str ts)) (Long/valueOf (str ts)) (c/to-long ts))
    (instance? Date ts) (.getTime ^Date ts)

    :else (System/currentTimeMillis)))

(defn parse-special-split-words [^String sep-str]
  (condp = sep-str
    "byte1" BYTE1
    "tab"   TAB
    "space" SPACE
    "semicolon" \;
    "pipe"    \|
    "comma"   \,

    :else BYTE1))


(defn split-type [format]
  (let [sep-str (get (:props format) "sep")]
    (if sep-str
      (parse-special-split-words sep-str)
      BYTE1)))


(defn cast-value [^String v]
  (cond
    (StringUtils/isNumeric v) (Long/valueOf v)
    (= v "true") true
    (= v "false") false
    :else v))

(defn vals-as-map [xs]
  (reduce (fn [m [k v]] (assoc m k (cast-value v))) {} (partition 2 xs)))

(defn  parse-format
  "Return record{:props, :type}"
  [^String input]
  (let [[type args] (string/split input #":")
        props (vals-as-map (string/split args #"[=;]"))]
    (->Format (str type) ^Map props)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;; format implementations

;;;;;;; default

(defmethod format-descriptor :default [_ _ format] format)
(defmethod dispose-format :default [_ _ _])

;;;;;;; txt support

(defn txtmsg->ts ^long [format msg]
  (let [ts-index (get (:props format) "ts")]
    (if (number? ts-index)
      (ts->long (nth msg ts-index))
      (System/currentTimeMillis))))

(defn txt->msg [format ^"[B" bts]
  (StringUtils/split (String. bts "UTF-8") (char (split-type format))))

(defmethod bts->msg "txt" [_ _ format bts]
  (let [msg (txt->msg format bts)
        ts (txtmsg->ts format msg)]
    (->FormatMsg format ts bts msg)))

(defmethod msg->string "txt" [_ _ _ ^FormatMsg msg]
  (String. ^"[B" (.getBts msg) "UTF-8"))

(defmethod msg->bts "txt" [_ _ _ ^FormatMsg msg]
  (.getBts msg))

