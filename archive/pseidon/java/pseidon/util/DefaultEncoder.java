package pseidon.util;

import java.io.UnsupportedEncodingException;

/**
 * 
 * Used as a encoding abstraction by the queue. <br/>
 * Supports Integer, Long and String. <br/>
 * Null instances return a zero byte array.
 * 
 */
public class DefaultEncoder implements Encoder<Object>{

	public static final Encoder<Object> DEFAULT_ENCODER = new DefaultEncoder();
	
	public byte[] encode(Object obj) {
		if(obj == null)
			return new byte[0];
		else if(obj instanceof Encodable)
			return ((Encodable)obj).getBytes();
	    else if(obj instanceof Long)
			return Bytes.toBytes(((Long)obj).longValue());
		else if(obj instanceof Integer)
			return Bytes.toBytes(((Integer)obj).intValue());
		else if(obj instanceof String)
			try {
				return Bytes.toBytes(obj.toString());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e.toString(), e);
			}
		else
			throw new RuntimeException("Type " + obj.getClass() + " is not supported");
	}

	
}
