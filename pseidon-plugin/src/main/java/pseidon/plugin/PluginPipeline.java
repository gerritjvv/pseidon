package pseidon.plugin;

import java.util.function.Function;

/**
 */
public interface PluginPipeline<T, R> extends Function<Message<T>, Message<R>> {

}
