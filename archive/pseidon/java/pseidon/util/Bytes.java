package pseidon.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import org.apache.commons.lang.StringUtils;

/**
 * 
 * Utility methods that convert between numeric bytes
 * 
 */
public final class Bytes {

	public static final char BYTE1 = (char) 0x001;

	public static final byte[] ZERO = toBytes(0);
	public static final byte[] NEW_LINE = "\n".getBytes();

	public static final byte[] toBytes(final byte[] v) {
		return v;
	}

	private static final long getI(char[] ch, long i) {
		return (i < ch.length) ? i : ch.length;
	}

	public static final void skip(Reader reader, long chars) throws IOException {
		char[] ch = new char[1024];
		int read = 0;
		int total = 0;
		System.out.println("Bytes.skip: " + chars);
		while ((read = reader.read(ch, 0, (int) getI(ch, chars - total))) > 0
				&& (total += read) < chars)
			System.out.println("Bytes.skip-222: total " + total + " read "
					+ read);
	}

	public static final long size(File file) {
		return file.length();
	}

	public static final void write(OutputStream out, byte[] bts)
			throws IOException {
		out.write(bts, 0, bts.length);
	}

	public static final void writeln(OutputStream out, byte[] bts)
			throws IOException {
		out.write(bts, 0, bts.length);
		out.write(NEW_LINE);
	}

	public static final byte[] toBytes(final String v)
			throws UnsupportedEncodingException {
		return v.getBytes("utf-8");
	}

	public static final byte[] toLongBytes(final long v){
		return toBytes(v);
	}
	
	public static final byte[] toBytes(final long v) {
		return new byte[] { (byte) (0xff & (v >> 56)),
				(byte) (0xff & (v >> 48)), (byte) (0xff & (v >> 40)),
				(byte) (0xff & (v >> 32)), (byte) (0xff & (v >> 24)),
				(byte) (0xff & (v >> 16)), (byte) (0xff & (v >> 8)),
				(byte) (0xff & v) };
	}

	public static final byte[] toBytes(final int v) {
		return new byte[] { (byte) (0xff & (v >> 24)),
				(byte) (0xff & (v >> 16)), (byte) (0xff & (v >> 8)),
				(byte) (0xff & v) };
	}
	
	/**
	 * Read the number from bts (this can be a 4 length int or a 8 length long)
	 * and returns the incremented value.
	 * 
	 * @param bts
	 * @return
	 */
	public static final byte[] inc(final byte[] bts, final int v) {
		if (bts.length == 4) {
			return toBytes(toInt(bts) + v);
		} else if (bts.length == 8) {
			return toBytes(toLong(bts) + v);
		} else
			throw new RuntimeException(
					"The byte array should be length 4 or 8 and not "
							+ bts.length + " possible value " + new String(bts));
	}

	public static final byte[] dec(final byte[] bts, final int v) {
		return inc(bts, v * -1);
	}

	/**
	 * Read the number from bts (this can be a 4 length int or a 8 length long)
	 * and returns the incremented value.
	 * 
	 * @param bts
	 * @return
	 */
	public static final byte[] inc(final byte[] bts, final long v) {
		if (bts.length == 4) {
			return toBytes(toInt(bts) + v);
		} else if (bts.length == 8) {
			return toBytes(toLong(bts) + v);
		} else
			throw new RuntimeException(
					"The byte array should be length 4 or 8 and not "
							+ bts.length + " possible value " + new String(bts));
	}

	public static final byte[] dec(final byte[] bts, final long v) {
		return inc(bts, v * -1);
	}

	public static final String toString(final byte[] bts)
			throws UnsupportedEncodingException {
		return getData(bts);
	}

	/**
	 * This function tries to guess the data type and return the correct String
	 * representation
	 * 
	 * @param bts
	 * @return
	 */
	public static final String getData(final byte[] bts) throws UnsupportedEncodingException {
		if (bts == null || bts.length == 0)
			return "";
		else if (bts.length == 4) {
			// could be int
			final String str = new String(bts, "UTF-8");
			if(StringUtils.isNumeric(str) || !StringUtils.isAsciiPrintable(str))
				return String.valueOf(toInt(bts));
			else 
				return str;
		} else if (bts.length == 8) {
			// could be long
			final String str = new String(bts, "UTF-8");
			if(StringUtils.isNumeric(str) || !StringUtils.isAsciiPrintable(str))
				return String.valueOf(toLong(bts));
			else
				return str;
		} else {
			return new String(bts, "UTF-8");
		}
	}

	public static final int toInt(final byte[] bts) {
		if (bts == null || bts.length == 0)
			return 0;
		else
			return (((bts[0] & 0xff) << 24) | ((bts[1] & 0xff) << 16)
					| ((bts[2] & 0xff) << 8) | (bts[3] & 0xff));
	}

	public static final long toLong(final byte[] bts) {
		if (bts == null || bts.length == 0)
			return 0L;
		else if (bts.length == 4)
			return (long) toInt(bts);
		else
			return (((long) (bts[0] & 0xff) << 56)
					| ((long) (bts[1] & 0xff) << 48)
					| ((long) (bts[2] & 0xff) << 40)
					| ((long) (bts[3] & 0xff) << 32)
					| ((long) (bts[4] & 0xff) << 24)
					| ((long) (bts[5] & 0xff) << 16)
					| ((long) (bts[6] & 0xff) << 8) | ((long) (bts[7] & 0xff)));
	}
}
