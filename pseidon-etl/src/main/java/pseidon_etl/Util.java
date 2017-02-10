package pseidon_etl;

import clojure.lang.Counted;
import clojure.lang.IFn;
import clojure.lang.ISeq;
import org.apache.commons.lang3.StringUtils;
import pseidon.plugin.AbstractBatchedPlugin;
import pseidon.plugin.PMessage;
import pseidon.plugin.Plugin;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Utility functions
 */
public class Util {

    /**
     * Return an new instance of AbstractBatchedPlugin
     */
    public static final Plugin asPlugin(IFn fn){
        return new IFnPlugin(fn);
    }

    public static final byte[] readFile(String file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        byte[] data = new byte[in.available()];
        try {
            in.read(data);
        } finally {
            in.close();
        }

        return data;
    }

    public static final boolean validTopic(String s) {
        return
                s != null
                        && s.length() > 0
                        && s.length() < 100
                        && !(StringUtils.contains(s, '\\') || StringUtils.contains(s, '/'));
    }

    /**
     * Fast check for nil or empty
     *
     * @param v
     * @return
     */
    public static final boolean notNilOrEmpty(Object v) {

        if (v == null)
            return false;
        else if (v instanceof Number)
            return true;
        else if (v instanceof String)
            return true;
        else if (v instanceof Counted)
            return ((Counted) v).count() > 0;
        else if (v instanceof ISeq)
            return ((ISeq) v).first() != null;
        return true;
    }

    /**
     * Fast padd zero function.<br/>
     * Is meant for dates and only works for positive numbers.
     *
     * @param v
     * @return
     */
    public static final String paddZero(long v) {
        return (v > 9) ? String.valueOf(v) : new StringBuilder(2).append('0').append(v).toString();
    }
}
