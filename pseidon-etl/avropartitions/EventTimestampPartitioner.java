package com.steppechange.dmp.kafka.connect.hdfs;

import com.google.common.base.Strings;
import io.confluent.common.config.ConfigException;
import io.confluent.connect.hdfs.partitioner.DefaultPartitioner;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Map;

/**
 * This class is implemented a partition based on "timestamp" field value. Joda time format is used in the path format.
 */
public class EventTimestampPartitioner extends DefaultPartitioner {

    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String EVENT_TIMESTAMP_PATTERN_NAME = "path.format";

    private String eventTimestampPartitionPattern;

    @Override
    public void configure(Map<String, Object> config) {
        super.configure(config);
        eventTimestampPartitionPattern = (String) config.get(EVENT_TIMESTAMP_PATTERN_NAME);
        if (Strings.isNullOrEmpty(eventTimestampPartitionPattern)) {
            throw new ConfigException(EVENT_TIMESTAMP_PATTERN_NAME, eventTimestampPartitionPattern, "Pattern to generate HDFS path have to be defined");
        }
    }

    @Override
    public String encodePartition(SinkRecord sinkRecord) {
        Struct struct = (Struct) sinkRecord.value();
        Long timestamp = struct.getInt64(TIMESTAMP_FIELD);

        DateTime dateTime = new DateTime(timestamp, DateTimeZone.UTC);

        return dateTime.toString(eventTimestampPartitionPattern);
    }
}
