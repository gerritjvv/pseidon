package pseidon.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pseidon.util.Functional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration interface. <br/>
 * {@link Plugin}(s) are passed an instance of the current context and configuration<br/>
 * on startup see {@link Plugin#init(Context)}
 */
public interface Context extends Map {

    <T> T getConf(String name);

    void debug(String... msgs);
    void info(String... msgs);
    void error(Throwable t, String... msgs);

    class DefaultCtx extends ConcurrentHashMap implements Context {

        private static final Logger LOG = LoggerFactory.getLogger(DefaultCtx.class);

        final Map<?, ?> conf;

        public DefaultCtx(){
            this(new HashMap());
        }
        public DefaultCtx(Map<?, ?> conf) {
            this.conf = conf;
        }


        @Override
        public <T> T getConf(String name) {
            return (T)conf.get(name);
        }

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
