package pseidon.plugin.pipeline;

import pseidon.plugin.PMessage;
import pseidon.plugin.Pipeline;
import pseidon.plugin.Plugin;
import pseidon.util.Functional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/**
 *
 */
public class DefaultPipeline<T> implements Pipeline<T>{

    private final Map<String, Plugin> mappings;
    private final Function<PMessage, PMessage> fn;

    public DefaultPipeline(Map<String, Plugin> mappings, Function<PMessage, PMessage> fn) {
        this.mappings = mappings;
        this.fn = fn;
    }


    @Override
    public void close() {
        mappings.values().forEach(Plugin::shutdown);
    }

    @Override
    public PMessage<T> apply(PMessage<T> message) {
        return fn.apply(message);
    }
}
