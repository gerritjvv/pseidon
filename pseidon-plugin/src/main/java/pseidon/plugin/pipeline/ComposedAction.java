package pseidon.plugin.pipeline;

import pseidon.plugin.PMessage;
import pseidon.util.Functional;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Composes from left to right
 */
public class ComposedAction implements Function<PMessage, PMessage>{

    final Function<PMessage, PMessage> fn;


    public ComposedAction(Collection<Function<PMessage, PMessage>> actions){
        Iterator<Function<PMessage, PMessage>> actionsIt = actions.iterator();

        if(!actionsIt.hasNext())
            fn = Functional.identity();
        else
            fn = Functional.reduce(
                    actionsIt,
                    actionsIt.next(),
                    (p1, p2) -> p1.andThen(p2));
    }

    @Override
    public PMessage apply(PMessage o) {
        return fn.apply(o);
    }
}
