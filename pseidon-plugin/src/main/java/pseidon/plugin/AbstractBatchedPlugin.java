package pseidon.plugin;

import java.util.Collection;

/**
 * Simplified plugin implementation that abstracts the user<br/>
 * from the PMessage mechanism.
 */
public abstract class AbstractBatchedPlugin<T, R> implements Plugin<T, R>{

    @Override
    public PMessage<R> apply(PMessage<T> msg) {
        return msg.updateMsgs(exec(msg.getType(), msg.getMessages()));
    }

    public abstract Collection<R> exec(String type, Collection<T> msg);

}
