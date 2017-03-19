package pseidon.plugin;


import java.util.Map;

/**
 * Describes a messages that has been passed through some basic formatted logic to extract the final Object.<br/>
 * <p>
 * in pseidon-etl this is done in the pseidon_logs table, and some logic is added to parse definitions like:
 * txt:ts=0;sep=tab => txt message separated by tab and timestamp in milliseconds at index 0
 * avro:ts=0;msg=1 =>  kafka avro formatted messages extracted as IndexedRecord ts = at index 0 and message at index 1
 */
public interface FormatMsg {

    Map getFormat();

    /**
     * Timestamp in java milliseconds
     */
    long getTs();

    /**
     * Get the original bytes
     */
    byte[] getBts();

    /**
     * Get the parsed message from bytes to Object.
     */
    Object getMsg();
}
