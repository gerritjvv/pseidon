package pseidon.util;

import java.io.UnsupportedEncodingException;

/**
 * 
 * Provides default decoders for Integer, Long and bytes.
 * 
 */
public class DefaultDecoders {

	public static final Decoder<byte[]> BYTES_DECODER = new Decoder<byte[]>() {

		public final byte[] decode(final byte[] bts) {
			return bts;
		}
	};
	
	public static final Decoder<Integer> INT_DECODER = new Decoder<Integer>() {

		public final Integer decode(final byte[] bts) {
			return Bytes.toInt(bts);
		}
	};

	public static final Decoder<Long> LONG_DECODER = new Decoder<Long>() {

		public final Long decode(final byte[] bts) {
			return Bytes.toLong(bts);
		}
	};

	public static final Decoder<String> STR_DECODER = new Decoder<String>() {

		public final String decode(final byte[] bts) {
			try {
				return Bytes.toString(bts);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e.toString(), e);
			}
		}
	};
	
}
