package pseidon_etl;

import clojure.lang.ILookup;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;

/**
 * Input message for all {@link pseidon.plugin.Plugin}s, and represents a single message read from kafka.
 */
public class TopicMsg extends PersistentArrayMap{

    /**
     * The kafka topic
     */
    final String topic;
    /**
     * The output codec if any, defined in pseidon_logs, e.g gzip, parquet, txt,<br/>
     * The codec is defined in the pseidon_logs table.
     */
    final String codec;

    /**
     * The message wrapper read from kafka
     */
    final FormatMsg msg;

    public TopicMsg(String topic, String codec, FormatMsg msg) {
        this.topic = topic;
        this.codec = codec;
        this.msg = msg;
    }

    @Override
    public Object valAt(Object key) {
        if (key instanceof Keyword) {
            switch (((Keyword) key).getName()) {
                case "topic":
                    return getTopic();
                case "codec":
                    return getCodec();
                case "msg":
                    return getMsg();
                default:
                    return super.get(key);
            }
        } else
            return super.valAt(key);    }


    public String getTopic() {
        return topic;
    }

    public String getCodec() {
        return codec;
    }

    public FormatMsg getMsg() {
        return msg;
    }

    @Override
    public String toString() {
        return "TopicMsg{" +
                "topic='" + topic + '\'' +
                ", codec='" + codec + '\'' +
                ", msg=" + msg +
                '}';
    }
}
