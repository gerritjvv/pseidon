# pseidon-hdfs-cloudera

Datasource plugin to push files into hdfs


## Usage

Consists of a Processor that receives messages for file uploads and uploads the message to hdfs.

## Message 

The message structure expected is:

//[bytes-seq ^String ds ids ^String topic  accept ^long ts ^long priority] 

| name | description |
| ---- | ------------|
| id   | ${local-file-absolute-path} |
| topic | "hdfs" the topic must be hdfs |

##HDFS Directories

The files are uploaded using 2 options:

OPTION 1:

Default: construct the remote file-name using default base and topic name for a log, under the base directory $hdfs-base-dir which is set in the pseidon.edn configuration, 
plus the combination of directories depending on the date in the filename.

```   
eg: $base/$topic/dt=<dt>/hr=<hr>
```   
OPTION 2:

construct the remote file-name using the configured base + path for a log,under the base directory $hdfs-base-dir which is set in the pseidon.edn configuration,
plus the combination of directories with path and date in the filename.
```
   eg: $base/$path_specified_for_topic/dt=<dt>/hr=<hr>
```

Where files are uploaded depends on:

| name | description |
| ---- | ----------- |
| hdfs-base-dir | the base directory to which the files will be uploaded |
| hdfs-dir-model | how the subdirectories for each file is created 1 = dt=yyyyMMdd/hr=yyyyMMddHH, 2 = year=yyyy/month=MM/day=dd/hour=HH |
| hdfs-local-file-model | how the type, date, file values are extracted from the local file 1 = type_id_hr_yyyyMMddHH.extension, 2 = type_yyyMMddHH.extension |
| hdfs-local-file-model-split | how the file is split to get the topic this must be a regex string either #"" or "" the default is "[_\.]" |

 
The final directory is:
```
Default:$basedir "/" $message.id[topic] "/" $datedir
with path specified for topic:$basedir "/" $topic_path"/"$datedir
```

## License


Distributed under the Eclipse Public License, the same as Clojure.
