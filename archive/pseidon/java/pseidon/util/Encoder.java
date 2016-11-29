package pseidon.util;

/**
 * 
 * Used by queue to abstract encoding to bytes
 * 
 * @param <T>
 */
public interface Encoder<T> {

	public byte[] encode(T obj);
	
}
