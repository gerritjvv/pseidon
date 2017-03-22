package pseidon.plugin;

/**
 * Describe a message read from kafka and all its metadata <br/>
 * The actual message is contained in the FormatMsg and describes<br/>
 * the timestamp (extracted by the message), the original bytes and the deserialized message.<br/>
 *
 * <p/>
 * The code for creating the FormatMsg depends on the implementation code reading it, for pseidon-etl<br/>
 * its the format code and formats are describes in the pseidon_logs database table.
 */
public interface TopicMsg {
    String getTopic();

    String getCodec();

    FormatMsg getMsg();
}
