package pseidon.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pseidon.util.Functional;

import java.util.Arrays;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration interface
 */
public interface Context extends Map {

    void debug(String... msgs);
    void info(String... msgs);
    void error(Throwable t, String... msgs);

    class DefaultCtx extends ConcurrentHashMap implements Context {

        private static final Logger LOG = LoggerFactory.getLogger(DefaultCtx.class);

        @Override
        public void debug(String... msgs) {
            if(LOG.isDebugEnabled())
                LOG.debug(asString(msgs));
        }

        @Override
        public void info(String... msgs) {
            if(LOG.isInfoEnabled())
                LOG.info(asString(msgs));
        }

        @Override
        public void error(Throwable t, String... msgs) {
            if(LOG.isErrorEnabled())
                LOG.error(asString(msgs), t);
        }

        private String asString(String[] msgs) {
            return Functional.reduce(
                    Arrays.asList(msgs),
                    new StringBuilder(),
                    (b, s) -> b.append(s).append(' ')).toString();
        }
    }

    default boolean booleanVal(Object v){
        if(v == null)
            return false;
        else{
            try {
                return Boolean.parseBoolean(v.toString());
            }catch (Exception e){
                return false;
            }
        }
    }
}
