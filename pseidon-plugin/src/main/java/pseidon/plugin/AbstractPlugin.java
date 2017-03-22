package pseidon.plugin;

import pseidon.util.Functional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Simplified plugin implementation that abstracts the user<br/>
 * from the PMessage mechanism.
 */
public abstract class AbstractPlugin<T, R> implements Plugin<T, R> {

    @Override
    public PMessage<R> apply(PMessage<T> msg) {
        return msg.updateMsgs(
                Functional.reduce(msg.getMessages(),
                        new ArrayList<R>(),
                        (l, v) -> {
                            l.add(exec(msg.getType(), v));
                            return l;
                        }));
    }

    public abstract R exec(String type, T msg);

}
