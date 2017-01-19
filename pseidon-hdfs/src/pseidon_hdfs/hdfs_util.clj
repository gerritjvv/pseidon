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


(defn get-default-permissions [^FileSystem fs]
  (let [^FsPermission default-perm (.applyUMask (FsPermission/getFileDefault) (FsPermission/getUMask (.getConf fs)))
        ^FsPermission perm (FsPermission. (.getUserAction default-perm)
                                          (.getUserAction default-perm)
                                          (.getOtherAction default-perm))]
    perm))

(defn ^FsPermission get-dir-permissions [^FileSystem fs {:keys [hdfs-dir-perms]}]
  (if hdfs-dir-perms
    (FsPermission. (str hdfs-dir-perms))
    (get-default-permissions fs)))

(defn ^FsPermission get-file-permissions [^FileSystem fs {:keys [hdfs-file-perms]}]
  (if hdfs-file-perms
    (FsPermission. (str hdfs-file-perms))
    (get-default-permissions fs )))

(defn hdfs-set-perms
  ([conf ^FileSystem fs path]
    (hdfs-set-perms conf fs path (get-file-permissions fs conf)))
  ([conf ^FileSystem fs path ^FsPermission perm]
   (info "setting perms " perm " on " path)
   (.setPermission fs (hdfs-path path) perm)))

(defn hdfs-mkdirs
  "Call mkdir -p on the parent dirs of the remote-file"
  [conf ^FileSystem fs dir]
  (debug "create directory " dir " with permissions " (get-dir-permissions fs conf))
  (.mkdirs fs (hdfs-path dir) (get-dir-permissions fs conf)))

(defn split-paths [path]
  (sort-by (comp count #(.toString %))
    (take-while
      (complement nil?)
      (iterate #(when % (.getParent ^Path %))
               (hdfs-path path)))))

(defn create-dir-if-not-exist
  "If the path does not exist, create it and call on-create"
  [conf ^FileSystem fs path & {:keys [on-create]}]
  (when (not (hdfs-path-exists? fs path))

    (doseq [sub-path (into [] (filter #(not (hdfs-path-exists? fs %)) (split-paths path)))]
      (info "creating dir: " sub-path)
      (hdfs-mkdirs conf fs sub-path)
      (hdfs-set-perms conf fs sub-path (get-dir-permissions fs conf)))

    (info "calling on-create")
    (on-create)))

(defn hdfs-copy-file
  "Copy From Local File without delete"
  [conf ^FileSystem fs src dest]
  (.copyFromLocalFile fs false (hdfs-path (.getAbsolutePath (io/file src))) (hdfs-path dest)))

(defn hdfs-rename
  "Rename the file form into to"
  [^FileSystem fs from to]
  (.rename fs (hdfs-path from) (hdfs-path to)))

(defn hdfs-delete [^FileSystem fs path]
  (.delete fs (hdfs-path path)))