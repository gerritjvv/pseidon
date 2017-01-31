package pseidon.plugin;

import java.util.function.Function;

/**
 * A runnable pipeline made up of {@link Plugin}(s).
 */
public interface Pipeline<T> extends Function<PMessage<T>, PMessage<T>>{

    void close();
}
