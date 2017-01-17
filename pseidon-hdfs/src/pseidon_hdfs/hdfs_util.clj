(ns
  ^{:doc "Hdfs utitlity functions"}
  pseidon-hdfs.hdfs-util
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :refer [info debug]])
  (:import (org.apache.hadoop.fs Path FileSystem)
           (java.io File FilePermission)
           (org.apache.commons.lang StringUtils)
           (org.apache.hadoop.fs.permission FsPermission)))


(defn add-slash [^String path]
  (if (StringUtils/startsWith path "/")
    path
    (str "/" path)))

(defn add-hdfs-prefix [^String path]
  {:pre [(string? path)]}
  (if (StringUtils/startsWith path "hdfs://")
    (add-slash path)
    (str "hdfs://" (add-slash path))))

(defn get-absolute-path [^String path]
  (if (StringUtils/startsWith path "hdfs://")
    path
    (.getAbsolutePath (io/file path))))

(defn ^Path hdfs-path
  "If path is Path then its returned otherwise (Path. path)"
  [path]
  (if (instance? Path path)
    path
    (Path. (get-absolute-path (str path)))))

(defn hdfs-path-exists?
  "True of the hdfs path exists"
  [^FileSystem fs path]
  (.exists fs (hdfs-path path)))

(defn local-path-exists?
  [path]
  (.exists (io/file (str path))))


(defn ^FsPermission get-permissions [{:keys [hdfs-path-perms] :or {hdfs-path-perms 0777}}]
  (FsPermission. (short hdfs-path-perms)))

(defn hdfs-mkdirs
  "Call mkdir -p on the parent dirs of the remote-file"
  [conf ^FileSystem fs dir]
  (.mkdirs fs (hdfs-path dir) (get-permissions conf)))

(defn create-dir-if-not-exist
  "If the path does not exist, create it and call on-create"
  [conf ^FileSystem fs path & {:keys [on-create]}]
  (when (not (hdfs-path-exists? fs path))
    (info "creating hdfs dir " path)
    (hdfs-mkdirs conf fs path)
    (info "calling on-create")
    (on-create)))

(defn hdfs-copy-file
  "Copy From Local File without delete"
  [conf ^FileSystem fs src dest]
  (.copyFromLocalFile fs false (hdfs-path (.getAbsolutePath (io/file src))) (hdfs-path dest)))

(defn hdfs-set-perms [conf ^FileSystem fs path]
  (debug "setting perms " (get-permissions conf) " on " path)
  (.setPermission fs (hdfs-path path) (get-permissions conf)))

(defn hdfs-rename
  "Rename the file form into to"
  [^FileSystem fs from to]
  (.rename fs (hdfs-path from) (hdfs-path to)))

(defn hdfs-delete [^FileSystem fs path]
  (.delete fs (hdfs-path path)))
