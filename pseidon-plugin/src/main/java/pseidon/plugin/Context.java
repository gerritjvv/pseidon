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

    /**
     * Globally defined plugins form the program that calls the PipelineParser.<br/>
     * This allows preset plugins to be defined, and also allows for easy integration<br/>
     * in environments where classes are not readily defined.
     */
    <T, R> Plugin getPlugin(String name);

    <T> T getConf(String name);

    void debug(String... msgs);

    void info(String... msgs);

    void error(Throwable t, String... msgs);

    static Context instance() {
        return new DefaultCtx();

    }

    static Context instance(Map<?, ?> conf) {
        return new DefaultCtx(conf, new HashMap<>());
    }

    static Context instance(Map<?, ?> conf, Map<String, Plugin> plugins) {
        return new DefaultCtx(conf, plugins);
    }

    class DefaultCtx extends ConcurrentHashMap implements Context {

        private static final Logger LOG = LoggerFactory.getLogger(DefaultCtx.class);

        final Map<?, ?> conf;
        final Map<String, Plugin> plugins;

        public DefaultCtx() {
            this(new HashMap(), new HashMap());
        }

        public DefaultCtx(Map<?, ?> conf, Map<String, Plugin> plugins) {
            this.conf = conf;
            this.plugins = plugins;
        }


        @Override
        public <T, R> Plugin getPlugin(String name) {
            return plugins.get(name);
        }

        @Override
        public <T> T getConf(String name) {
            return (T) conf.get(name);
        }

        @Override
        public void debug(String... msgs) {
            if (LOG.isDebugEnabled())
                LOG.debug(asString(msgs));
        }

        @Override
        public void info(String... msgs) {
            if (LOG.isInfoEnabled())
                LOG.info(asString(msgs));
        }

        @Override
        public void error(Throwable t, String... msgs) {
            if (LOG.isErrorEnabled())
                LOG.error(asString(msgs), t);
        }

        private String asString(String[] msgs) {
            return Functional.reduce(
                    Arrays.asList(msgs),
                    new StringBuilder(),
                    (b, s) -> b.append(s).append(' ')).toString();
        }
    }

    default boolean booleanVal(Object v) {
        if (v == null)
            return false;
        else {
            try {
                return Boolean.parseBoolean(v.toString());
            } catch (Exception e) {
                return false;
            }
        }
    }
}
