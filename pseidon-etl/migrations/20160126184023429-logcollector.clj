;; migrations/20160126184023429-logcollector.clj

(defn up []
  ["CREATE TABLE IF NOT EXISTS `kafka_formats` (
      `log` varchar(255) NOT NULL,
      `format` enum('gpb','json') DEFAULT NULL,
      `output_format` enum('parquet','json') DEFAULT NULL,
      `hive_table_name` varchar(255) DEFAULT NULL,
      `hive_url` varchar(255) DEFAULT NULL,
      `hive_user` varchar(255) DEFAULT NULL,
      `hive_password` varchar(255) DEFAULT NULL,
      `base_partition` varchar(255) DEFAULT NULL,
      `log_partition` varchar(255) DEFAULT NULL,

      PRIMARY KEY (`log`)
    )"

    "CREATE TABLE IF NOT EXISTS `kafka_logs` (
       `log` varchar(200) DEFAULT NULL,
       `type` varchar(10) DEFAULT NULL,
       `enabled` tinyint(1) DEFAULT '1',
       `log_group` varchar(100) DEFAULT NULL,
       `format` varchar(10) DEFAULT NULL
     )"


     ;;----------- log puller tables
     "CREATE TABLE IF NOT EXISTS `log_pullers` (
        `pseidon_log_puller` varchar(100) DEFAULT NULL,
        `host` varchar(100) DEFAULT NULL,
        `port` int(11) DEFAULT NULL,
        `source` varchar(100) DEFAULT NULL,
        `log` varchar(50) DEFAULT NULL,
        `enabled` tinyint(1) DEFAULT '1',
        UNIQUE KEY `log_collectors` (`host`,`log`)
      )"

      "CREATE TABLE IF NOT EXISTS `log_puller_batchids` (
         `host` varchar(100) DEFAULT NULL,
         `slave` varchar(100) DEFAULT NULL,
         `log` varchar(50) DEFAULT NULL,
         `ts` bigint(20) DEFAULT NULL,
         UNIQUE KEY `log_collector_batchids_1` (`host`,`slave`,`log`)
       )"

     ;;----------- priority pipeline dbs ;;
     "CREATE TABLE IF NOT EXISTS `redis_server` (
        `app_id` int(100) unsigned NOT NULL AUTO_INCREMENT,
        `app_name` varchar(100) CHARACTER SET utf8 NOT NULL DEFAULT '',
        `redis_ip` varchar(50) CHARACTER SET utf8 NOT NULL DEFAULT '',
        `redis_port` int(10) unsigned NOT NULL DEFAULT '6379',
        `redis_db` int(10) unsigned NOT NULL DEFAULT '0',
        `cmd_ip` varchar(100) DEFAULT NULL,
        `ppl_server` varchar(64) DEFAULT NULL,
        PRIMARY KEY (`app_id`)
      )"

      "CREATE TABLE IF NOT EXISTS `source_logs` (
         `log_id` int(11) NOT NULL,
         `source_id` int(11) NOT NULL,
         `log_name` varchar(128) DEFAULT NULL,
         `version` int(11) DEFAULT NULL,
         `file_pattern` varchar(256) DEFAULT NULL,
         `additional_storage_dirs` varchar(1024) DEFAULT NULL,
         `time_type` varchar(16) DEFAULT NULL,
         `parse_script_name` varchar(64) DEFAULT NULL,
         `execute_string` varchar(512) DEFAULT NULL,
         `file_pattern_ext` varchar(1024) DEFAULT NULL,
         `active` int(11) DEFAULT NULL,
         PRIMARY KEY (`log_id`),
         KEY `source_id` (`source_id`),
         CONSTRAINT `source_logs_ibfk_1` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`)
       )"

       "CREATE TABLE IF NOT EXISTS `data_source` (
          `id` int(11) NOT NULL,
          `source_name` varchar(128) NOT NULL,
          `transfer_protocol` varchar(16) DEFAULT NULL,
          `host` varchar(128) DEFAULT NULL,
          `port` int(11) DEFAULT NULL,
          `directory` varchar(128) DEFAULT NULL,
          `user_id` varchar(32) DEFAULT NULL,
          `password` varchar(128) DEFAULT NULL,
          `check_filename` varchar(128) DEFAULT NULL,
          `navigate_tree_flag` int(11) DEFAULT NULL,
          `dest_directory` varchar(128) DEFAULT NULL,
          `filedate_pattern` varchar(1024) DEFAULT NULL,
          `end_pattern_data` varchar(64) DEFAULT NULL,
          `hash_before_zip` char(1) DEFAULT NULL,
          `log_to` varchar(128) DEFAULT NULL,
          `redis_db` int(3) DEFAULT NULL,
          `queue_directory` varchar(128) DEFAULT NULL,
          `log_parser_name` varchar(64) DEFAULT NULL,
          `active` char(1) DEFAULT NULL,
          `location_api` varchar(2) DEFAULT 'DE',
          `upload_interval_mins` int(11) DEFAULT NULL,
          `redis_host` varchar(128) DEFAULT NULL,
          `redis_port` int(11) DEFAULT NULL,
          `private_key_file` varchar(256) DEFAULT NULL,
          PRIMARY KEY (`id`),
          UNIQUE KEY `source_name` (`source_name`)
        )"

       "CREATE TABLE IF NOT EXISTS `priority_pipeline_log_qtype` (
          `id` int(10) NOT NULL AUTO_INCREMENT,
          `log_id` int(10) NOT NULL,
          `queue_type` varchar(50) NOT NULL,
          `active` char(1) NOT NULL DEFAULT 'Y',
          `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (`id`),
          UNIQUE KEY `UNQ_LOGID_QTYPE` (`log_id`,`queue_type`)
        ) ENGINE=MyISAM AUTO_INCREMENT=128 DEFAULT CHARSET=latin1 COMMENT='xref logid and qtypes'"

        "CREATE TABLE IF NOT EXISTS `targeting_strings` (
           `url` text NOT NULL,
           `query_string` text NOT NULL,
           `id` int(11) NOT NULL AUTO_INCREMENT,
           PRIMARY KEY (`id`)
         )"

   ;    [:ts :server_name :stat_name :occurances :avg_count :avg_size]

   "CREATE TABLE IF NOT EXISTS `pseidon_heap_record` (
     `ts` TIMESTAMP,
     `server_name` varchar(255) not null,
     `stat_name` varchar(100) not null,
     `occurances` int,
     `avg_count` int,
     `avg_size` bigint,
     PRIMARY KEY (ts, server_name, stat_name)
   )"])

(defn down []
  [])
