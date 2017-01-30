package pseidon.plugin.pipeline;

import pseidon.plugin.Message;
import pseidon.plugin.PluginPipeline;

import java.io.File;
import java.util.function.Function;

public class DefaultPipeline<T, R> implements PluginPipeline<T, R> {

    final Function<Message<T>, Message<R>> fn;

    public DefaultPipeline(Function<Message<T>, Message<R>> fn) {
        this.fn = fn;
    }

    @Override
    public Message<R> apply(Message<T> message) {
        return fn.apply(message);
    }

    public static final <T, R> PluginPipeline<T, R> create(File ednFile){

        return null;
    }
}
