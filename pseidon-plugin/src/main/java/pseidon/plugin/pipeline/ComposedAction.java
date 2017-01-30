package pseidon.plugin.pipeline;

import pseidon.plugin.Context;
import pseidon.plugin.Message;
import pseidon.util.Functional;
import pseidon.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Composes from left to right
 */
public class ComposedAction implements Function<Message, Message>{

    final Function<Message, Message> fn;


    public ComposedAction(Collection<Function<Message, Message>> actions){
        Iterator<Function<Message, Message>> actionsIt = actions.iterator();

        if(!actionsIt.hasNext())
            fn = Functional.identity();
        else
            fn = Functional.reduce(
                    actionsIt,
                    actionsIt.next(),
                    (p1, p2) -> p1.andThen(p2));
    }

    @Override
    public Message apply(Message o) {
        return fn.apply(o);
    }
}
