package pseidon_etl;

import com.google.common.base.Throwables;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 */
public class AvroSerializer {

    public static final byte[] serialize(Schema schema, GenericRecord record){

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
        Encoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        try {
            writer.write(record, encoder);

            encoder.flush();
            out.close();

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        return out.toByteArray();
    }
}
