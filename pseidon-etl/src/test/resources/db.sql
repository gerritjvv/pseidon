CREATE TABLE IF NOT EXISTS `pseidon_logs` (
  `log` varchar(100) NOT NULL,
  `format` varchar(200) DEFAULT NULL,
  `output_format` varchar(200) DEFAULT NULL,
  `base_partition` varchar(200) DEFAULT NULL,
  `log_partition` varchar(200) DEFAULT NULL,
  `hive_table_name` varchar(200) DEFAULT NULL,
  `hive_url` varchar(255),
  `hive_user` varchar(200) DEFAULT NULL,
  `hive_password` varchar(200) DEFAULT NULL,
  `quarantine` varchar(200) DEFAULT "/tmp/pseidon-quarantine",
  `log_group` varchar(100) DEFAULT 'default',
  `enabled`  tinyint(1) DEFAULT '1',
  PRIMARY KEY (`log`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1


INSERT INTO pseidon_logs (log, format, output_format, base_partition, log_partition)
VALUES ("mytopic", "txt:sep=,;ts=0", "gzip", "/tmp/", "mytopic");

