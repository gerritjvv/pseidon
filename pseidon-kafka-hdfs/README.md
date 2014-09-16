# pseidon-kafka-hdfs

Reads from kafka and sends the data in rolled files to hdfs

## Usage

### Encoders and Decoders:

Data read from kafka can be decoded and encoded into different formats, e.g. if the data in kafka is in nippy, 
and you want to save it as json to hadoop, you can use the :nippy decoder and the json encoder.

The default encoder and decoder are identity functions and does nothing, encoders and decoders can be specified
on a topic per topic basis and or globally.

Configuration is done via a database and or the properties file.
Configuration in the database override the properties file configurations.

#### Properties file:

The configuration properties are:
```
kafka-hdfs-$topic-encoder
kafka-hdfs-$topic-decoder
kafka-hdfs-default-encoder
kafka-hdfs-default-decoder
```

#### Database configuration

There are four tables:

* kafka_log_encoders

```
+-------+----------------------------------------------+------+-----+---------+-------+
| Field | Type                                         | Null | Key | Default | Extra |
+-------+----------------------------------------------+------+-----+---------+-------+
| log   | varchar(255)                                 | NO   | PRI | NULL    |       |
| type  | enum('default','json','json-csv','json-tsv') | NO   | PRI | NULL    |       |
| data  | text                                         | YES  |     | NULL    |       |
+-------+----------------------------------------------+------+-----+---------+-------+
```

* source_log_fields

```
+------------+-------------+------+-----+---------+-------+
| Field      | Type        | Null | Key | Default | Extra |
+------------+-------------+------+-----+---------+-------+
| log_id     | int(11)     | NO   | PRI | NULL    |       |
| field_id   | int(11)     | NO   | PRI | NULL    |       |
| field_name | varchar(64) | NO   |     | NULL    |       |
| data_type  | varchar(28) | YES  |     | NULL    |       |
+------------+-------------+------+-----+---------+-------+
```

* source_logs

```
+-------------------------+---------------+------+-----+---------+-------+
| Field                   | Type          | Null | Key | Default | Extra |
+-------------------------+---------------+------+-----+---------+-------+
| log_id                  | int(11)       | NO   | PRI | NULL    |       |
| log_name                | varchar(128)  | YES  |     | NULL    |       |
+-------------------------+---------------+------+-----+---------+-------+
```

* hdfs_log_partitions;

```
+---------------+--------------+------+-----+---------+-------+
| Field         | Type         | Null | Key | Default | Extra |
+---------------+--------------+------+-----+---------+-------+
| logname       | varchar(100) | NO   | PRI | NULL    |       |
| basepartition | varchar(200) | YES  |     | NULL    |       |
| logpartition  | varchar(200) | YES  |     | NULL    |       |
+---------------+--------------+------+-----+---------+-------+
```

By default the properties file should contain the default encoders that should be used for most of the logs, and only use the database tables
to condigure encoders for logs that do not follow the default encodings e.g. default json and then add logs that require to csv/tsv encoding from json.

#### How to configure an encoder for a log via the db?

The kafka_log_encoders table has three fields log (log name), type (the encoder type) and data.

The data field can be left null or filled in depending on the encoder type.

__json-csv/tsv data definition__

For csv/tsv a mapping from json to csv is required such that the data field needs to contain the mapping (in json format).
This assumes that the default decoder is json. The data format should be "[[\"$keypath\", default-value] ...]", where keypath can be a path of keys pointing to the value
e.g. to get at value 1 in the json {"a": {"b": 1} } the keypath should be "a/b".

There is an extension to this, that if the data is empty and the encoders json-csv/tsv are specified the source_logs and source_log_fields tables will be queried.
The latter allows for a more extensive definition of the logs and their columns.

The source_logs contains the log name entries and for each column in a log and entry is made in the source_log_fields table.

IMPORTANT:
The field_id defines the field order only, and all columns should be present i.e. if the csv should have 10 columns there should be exactly 10 entries in the source_log_fields



### Time parsers

The time stamp can be taken from the kafka message, or the current time when the message is read can be used.

The property to specify this is ```kafka-hdfs-ts-parser or kafka-hdfs-$topic-ts-parser``` possible values are ```:now``` this takes the current time stamp,
```:obj``` this takes the path from a object map message structure, note to use this the decoder must be ```:json``` or ```:nippy```.

#### Timestamp path

The library doesn't make assumption of where the timestamp is, and so its required to give a timestamp path, 
this can be done on a topic basis or globally for all messages.

```
:kafka-hdfs-$topic-ts-parser-args
:kafka-hdfs-ts-parser-args
```

The value must be a vector or list.

The default used s ```clojure ["ts"]```

### How to configure HDFS Log Partitions
The default configuration uses a base directory and places the log under a directory named as topic.

The base directory can be configured as the following property in pseidon.edn: 
```
hdfs-base-dir
e.g.: /$base_dir/$topic_dir
```

The partitions can also be made configurable for each log using the hdfs_log_partitions table.
This table contains the mapping for a log and the final destination where files  will be stored under hdfs.
The mapping contains:
```
log_name : basepartition and  logpartition
e.g.: The files for a certain topic log will be stored at the following location locally and hdfs: /$base_partition/$log_partition
```
 
The log partition table can be configured using the following properties in pseidon.edn to fetch the entries from hdfs_log_partitions table:
```
:hdfs-partition-db-subname 
:hdfs-partition-db-user 
:hdfs-partition-db-password
```

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
