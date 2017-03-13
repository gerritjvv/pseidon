package pseidon_etl;

import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;

import java.util.Arrays;
import java.util.Map;

/**
 * Represents a single message read from kafka.
 */
public class FormatMsg extends PersistentArrayMap{

    /**
     *The input format that was used to read the message and deserialize it to a Map object, e.g avro
     */
    private final Format format;
    /**
     * The message timestamp, extracted as per the format
     */
    private final long ts;

    /**
     * The original bytes read from kafka
     */
    private final byte[] bts;
    /**
     * The serialized message read from the {@link #bts} and deserialized with {@link #format}
     */
    private final Object msg;

    public FormatMsg(Format format, long ts, byte[] bts, Object msg) {
        this.format = format;
        this.ts = ts;
        this.bts = bts;
        this.msg = msg;
    }

    @Override
    public Object valAt(Object key) {
        if(key instanceof Keyword){
            switch (((Keyword)key).getName()){
                case "format":
                    return getFormat();
                case "ts":
                    return getTs();
                case "bts":
                    return getBts();
                case "msg":
                    return msg;
                default:
                    return super.get(key);
            }
        }
        else
            return super.get(key);
    }

    public Format getFormat() {
        return format;
    }

    /**
     * Time in milliseconds
     */
    public long getTs() {
        return ts;
    }

    public byte[] getBts() {
        return bts;
    }

    public Object getMsg() {
        return msg;
    }

    @Override
    public String toString() {
        return "FormatMsg{" +
                "format=" + format +
                ", ts=" + ts +
                ", bts=" + Arrays.toString(bts) +
                ", msg=" + msg +
                '}';
    }

    /**
     * (defrecord Format [^String type ^Map props])
     */
    public static class Format extends PersistentArrayMap{

        private final String type;
        private final Map<?, ?> props;


        public Format(String type, Map<?, ?> props) {
            this.type = type;
            this.props = props;
        }

        @Override
        public Object valAt(Object key) {
            if(key instanceof Keyword){
                switch (((Keyword)key).getName()){
                    case "props":
                        return getProps();
                    case "type":
                        return getType();
                    default:
                        return super.get(key);
                }
            }
            else
                return super.get(key);
        }


        public String getType() {
            return type;
        }

        public Map<?, ?> getProps() {
            return props;
        }

        @Override
        public String toString() {
            return "Format{" +
                    "type='" + type + '\'' +
                    ", props=" + props +
                    '}';
        }
    }
}
