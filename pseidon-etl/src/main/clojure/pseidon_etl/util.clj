(ns pseidon-etl.util
  (:import
    [java.io File]
    [java.nio.file Files Paths]
    [java.nio.file.attribute BasicFileAttributes]
    (java.nio.file Files LinkOption NoSuchFileException)
    (java.util.concurrent.atomic AtomicLong))
  (:require
    [clojure.tools.logging :refer [info fatal]]
    [clojure.java.io :as io]))

(set! *unchecked-math* true)

(defprotocol ICountableState
  (-inc! [_])
  (-set-state! [_ state])
  (-inc-error! [_ error])
  (-error-count [_])
  (-error [_])
  (-state [_])
  (-cnt [_]))

(deftype CountableState [^:volatile-mutable state ^:volatile-mutable ^long i ^:volatile-mutable ^long error-count ^:volatile-mutable error]
  ICountableState
  (-inc! [_]
    (set! i (+ i 1)))
  (-inc-error! [_ e]
    (set! error-count (+ error-count 1))
    (set! error e))
  (-error-count [_] error-count)
  (-error [_] error)
  (-set-state! [_ state2] (set! state state2))
  (-state [_] state)
  (-cnt [_] i))

(defn countable-state [init-state init-count]
  (->CountableState init-state (long init-count) 0 nil))

(defn state-inc! [state]
  (when state
    (-inc! state)
    state))

(defn state-error-inc! [state error]
  (when state
    (-inc-error! state error)
    state))

(defn state-set! [state new-state]
  (when state
    (-set-state! state new-state)
    state))

(defn state-count [state] (if state (-cnt state) 0))

(defn state-error-count [state] (if state (-error-count state) 0))

(defn state [state] (when state (-state state)))

(defn state-error [state] (when state (-error state)))

(defn file-attributes
  "keys returned creation-time, size"
  [file]
  (try
    (let [^File file-obj (io/file file)
          ^BasicFileAttributes attrs (Files/readAttributes
                                       (Paths/get (.getAbsolutePath file-obj) (into-array String []))
                                       BasicFileAttributes
                                       (into-array LinkOption []))]
      {:creation-time (.toMillis (.creationTime attrs))
       :size          (.size attrs)})
    (catch NoSuchFileException _ {})))


(defn str-matches?
  "If the string x contains any of the string args return true"
  [x args]
  (some #(.contains (str x) %) args))

(defn local-file-seq [dir matches excludes]
  (->> dir io/file file-seq (filter (fn [file] (let [^File file-obj (io/file file)]
                                                 (and (str-matches? (.getName file-obj) matches)
                                                      (not (str-matches? (.getName file-obj) excludes))
                                                      (.exists file-obj)))))))

(defn any-files?
  "True if any local files with a file name that contains any of the items in matches"
  [dir matches excludes]
  {:pre [dir (coll? matches) (coll? excludes)]}
  (pos? (count (local-file-seq dir matches excludes))))

(defn wait-till-no-files [dir]
  (let [file-suffixes ["gz_" "parquet_"]
        excludes [".crc"]]
    (info "!!!!!!! Waiting for open files to be closed, open: " (local-file-seq (io/file dir) file-suffixes excludes))

    (while (any-files? (io/file dir) file-suffixes excludes)
      (info "Files open are " (local-file-seq (io/file dir) file-suffixes excludes))
      (Thread/sleep 2000)))
  (info "file open activity has ceased"))

(defn wait-zero-activity! [msg ^AtomicLong activity-counter]
  (loop [v1 -1]
    (info "Waiting for " msg " activity to cease")
    (Thread/sleep 1000)
    (let [v2 (.get activity-counter)]
      (if-not (= v1 v2)
        (recur v2))))
  (info msg "acitivity ceased"))

(defn fatal-error
  "Print a fatal error and then runs exit -1"
  [e]
  (fatal e "Exiting application")
  (spit "/tmp/fatalerrors" (str e "\n") :append true)
  (System/exit -1))

(defmacro multi-assoc
          "Convert a series of assocs into a transient, assoc!, persisent commands
           usage
           (multi-assoc {} :a 1 :b 2 :c 3) => (let [p (transient! {})]
                                                (assoc! p :a 1)
                                                (assoc! p :b 2)
                                                (assoc! p :c 3)
                                                (persistent! p))"

          [m & kvs]
          (let [assocs (reduce (fn [s [k v]]
                                   (conj s (list 'assoc! k v))) [] (partition 2 kvs))]
               `(-> ~m
                    ~transient
                    ~@assocs
                    ~persistent!)))