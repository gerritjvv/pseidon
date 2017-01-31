package pseidon.plugin;

import java.util.function.Function;

/**
 * Plugins should implement this interface.<br/>
 *
 * <pre>
 *     public class IncPlugin implements Plugin<Integer, Integer>{
 *
 *      @Override
 *      public PMessage<Integer> apply(PMessage<Integer> integerMessage) {
 *          int v = integerMessage.getSingleMessage() + 1;
 *
 *          System.out.println("IncPlugin:apply: " + integerMessage.getSingleMessage() + " -> " + v);
 *
 *          return integerMessage.updateMsgs(Arrays.asList(v));
 *      }
 *    }
 *
 * </pre>
 */
public interface Plugin<T, R> extends Function<PMessage<T>, PMessage<R>> {

    /**
     * Called on startup
     * @param ctx
     */
    default void init(Context ctx){}

    /**
     * Called on shutdown
     */
    default void shutdown(){}

    /**
     * Receives and returns a {@link PMessage} object.
     * @param msg type: is the type of the log
     * @return
     */
    PMessage<R> apply(PMessage<T> msg);

}
