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

           Format avro-txt
             see pseidon-etl.avro/avro-format
          "}
  pseidon-etl.formats
  (:require [clojure.string :as string]
	    [clojure.tools.logging :refer [info]]
            [clj-time.coerce :as c])
  (:import (java.util Map Date Arrays)
           (org.apache.commons.lang3 StringUtils)
           (pseidon_etl FormatMsgImpl)
           (org.apache.avro.generic IndexedRecord)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;; Data Types and multimethods

(defonce ^Class ARRAY-TYPE (Class/forName "[Ljava.lang.String;"))

(defrecord Format [^String type ^Map props])

(defmulti bts->msg (fn [conf topic format bts] (:type format)))

(defmulti msg->string (fn [conf topic format msg] (:type format)))

(defmulti msg->bts (fn [conf topic format msg] (:type format)))

(defmulti format-descriptor (fn [state conf format] (:type format)))

(defmulti dispose-format (fn [state conf format] (:type format)))

(declare parse-format)

(defn ^FormatMsgImpl ->FormatMsg [format ^long ts ^"[B" bts msg]
  (FormatMsgImpl. format ts bts msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;; utility functions

(defonce BYTE1 \u0001)
(defonce TAB \tab)
(defonce SPACE \space)


;;;; indexed protocol to add support for IndexedRecord
(defprotocol IIndexed
  (-nth [v i]))

(extend-protocol

  IIndexed

  IndexedRecord
  (-nth [r i] (.get ^IndexedRecord r (int i)))

  Object
  (-nth [r i] (nth r i)))

(defn cast-long [v]
  (if (instance? Number v)
    (long v)
    (Long/parseLong (str v))))

(defn ts->long ^long [ts]
  (cond
    (number? ts) (long ts)
    (string? ts) (if (StringUtils/isNumeric (str ts)) (Long/valueOf (str ts)) (c/to-long ts))
    (instance? Date ts) (.getTime ^Date ts)

    :else (System/currentTimeMillis)))

(defn ts-parser
  "Return a timestamp parser function"
  [props]
  (cond
    (or (get props "ts") (get props "ts_ms")) (let [index (Integer/parseInt (str (get props "ts" (get props "ts_ms"))))]

                                                (if (> index -1)
                                                  (fn [record] (ts->long (-nth record (int index))))
                                                  (fn [_] (System/currentTimeMillis))))

    (get props "ts_sec") (let [index (Integer/parseInt (str (get props "ts_sec")))]

                           (if (> index -1)
                             (fn [record]
                               (* (long (ts->long (-nth record (int index)))) 1000))
                             (fn [_]
                               (System/currentTimeMillis))))

    :else
    (throw (RuntimeException. (str "format must contain either a ts, ts_sec or ts_ms parameter to identify the index of the timestamp")))))


(defn parse-special-split-words [^String sep-str]
  (condp = sep-str
    "byte1" BYTE1
    "tab" TAB
    "space" SPACE
    "semicolon" \;
    "pipe" \|
    "comma" \,

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

(defn parse-format
  "Return record{:props, :type}"
  [^String input]
  (let [[type args] (string/split input #":")
        props (vals-as-map (string/split args #"[=;]"))]

    (->Format (str type) ^Map props)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;; format implementations

;;;;;;; default

(defn bytes-parser
  "The inverse of msg-parser, and returns a function msg-format,msg[] -> byte[]"
  [props]
  (let [msg-index (cast-long (get props "msg" "-1"))]
    (if (pos? msg-index)
      (fn [_ ^FormatMsgImpl msg] (.getBytes (str (:msg msg)) "UTF-8"))
      (fn [_ ^FormatMsgImpl msg]
        (:bts msg)))))


(defn msg-parser
  "If msg is undefined or -1 an identify function is returned, otherwise a function that looks up the index msg is returned"
  [props]
  (let [msg-index (cast-long (get props "msg" "-1"))]

    (if (pos? msg-index)
      (fn [_ msg] (-nth msg msg-index))
      (fn [_ msg] msg))))

(defn txt-msg-parser
  [props]
  (let [msg-index (cast-long (get props "msg" "-1"))]
    (if (pos? msg-index)
      (fn [_ msg] (-nth msg msg-index))
      (fn [^"[B" bts msg] (String. bts "UTF-8")))))

(defn default-format-descriptor [format]
  (assoc
    format
    :bts-parser (bytes-parser (:props format))
    :ts-parser (ts-parser (:props format))
    :msg-parser (msg-parser (:props format))))

(defmethod format-descriptor :default [_ _ format]
  (default-format-descriptor format))

(defmethod format-descriptor "txt" [_ _ format]
  (assoc
    (default-format-descriptor format)
    :msg-parser (txt-msg-parser (:props format))))

(defmethod dispose-format :default [_ _ _])

;;;;;;; txt support

(defn split-message [format ^"[B" bts]
  (StringUtils/split (String. bts "UTF-8") (char (split-type format))))

(defmethod bts->msg "txt" [_ _ format bts]
  (let [split-msg (split-message format bts)

        msg ((:msg-parser format) bts split-msg)
        ts ((:ts-parser format) split-msg)]
    ;(info "Got: " (String. (bytes bts)) " ts= " ts " msg= " msg   "format= " format)
    (->FormatMsg format ts bts msg)))

(defmethod msg->string "txt" [_ _ format ^FormatMsgImpl msg]
  (let [wrappedMsg (:msg msg)]
    (if (instance? String wrappedMsg)
      wrappedMsg
      (StringUtils/join ^"[Ljava.lang.String;" (wrappedMsg) (char (split-type format))))))

(defmethod msg->bts "txt" [_ _ format ^FormatMsgImpl msg]
  ((:bts-parser format) format msg))

