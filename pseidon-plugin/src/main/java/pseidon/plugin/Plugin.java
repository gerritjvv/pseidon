package pseidon.plugin;

import java.util.function.Function;

/**
 */
public interface Plugin<T, R> extends Function<Message<T>, Message<R>> {

    void init(Context ctx);
    void shutdown(Context ctx);
}
