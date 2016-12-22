package pseidon.util;

/**
 * 
 * Used by queue to abstract decoding from bytes
 * 
 * @param <T>
 */
public interface Decoder<T> {

	public T decode(byte[] bts);
	
}
