package com.steppechange.dmp.kafka.connect.hdfs;

import io.confluent.connect.avro.AvroData;
import io.confluent.connect.hdfs.Format;
import io.confluent.connect.hdfs.HdfsSinkConnectorConfig;
import io.confluent.connect.hdfs.RecordWriter;
import io.confluent.connect.hdfs.RecordWriterProvider;
import io.confluent.connect.hdfs.SchemaFileReader;
import io.confluent.connect.hdfs.hive.HiveMetaStore;
import io.confluent.connect.hdfs.hive.HiveUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;

import java.io.IOException;

/**
 * This class is implemented a text file format. "line" field is kept in the result file.
 */
public class PlainTextFormat implements Format {

    private static final String PAYLOAD_FIELD = "line";

    @Override
    public RecordWriterProvider getRecordWriterProvider() {
        return new RecordWriterProvider() {
            @Override
            public String getExtension() {
                return ".txt";
            }

            @Override
            public RecordWriter<SinkRecord> getRecordWriter(final Configuration conf, final String fileName, SinkRecord record, AvroData avroData) throws IOException {
                return new RecordWriter<SinkRecord>() {
                    private final Path path = new Path(fileName);
                    private final FSDataOutputStream hadoop;
                    {
                        // assign group permission the same as for user permission from default permissions
                        FsPermission defaultPermission = FsPermission.getFileDefault().applyUMask(FsPermission.getUMask(conf));
                        FsPermission permission = new FsPermission(defaultPermission.getUserAction(), defaultPermission.getUserAction(), defaultPermission.getOtherAction());
                        hadoop = FileSystem.create(path.getFileSystem(conf), path, permission);
                    }

                    @Override
                    public void write(SinkRecord sinkRecord) throws IOException {
                        Struct struct = (Struct) sinkRecord.value();
                        String line = struct.getString(PAYLOAD_FIELD);

                        hadoop.write(line.getBytes());
                        hadoop.write("\n".getBytes());
                    }

                    @Override
                    public void close() throws IOException {
                        hadoop.close();
                    }
                };
            }
        };
    }

    @Override
    public SchemaFileReader getSchemaFileReader(AvroData avroData) {
        return null;
    }

    @Override
    public HiveUtil getHiveUtil(HdfsSinkConnectorConfig config, AvroData avroData, HiveMetaStore hiveMetaStore) {
        return null;
    }
}