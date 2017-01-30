package pseidon.plugin.pipeline;

import pseidon.util.Functional;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Runs actions one of the other, passing in the same input and discarding the output values.
 */
public class AllAction<T> implements Function<T, T> {

    final Function<T, T> fn;


    public AllAction(Collection<Function> actions) {

        int size = actions.size();

        if (size == 0)
            fn = Functional.identity();
        else if (size == 1)
            fn = actions.iterator().next();
        else if (size == 2) {
            Function fn1 = actions.iterator().next();
            Function fn2 = actions.iterator().next();

            fn = (v) -> {
                fn1.apply(v);
                fn2.apply(v);
                return v;
            };
        } else if (size == 3) {
            Function fn1 = actions.iterator().next();
            Function fn2 = actions.iterator().next();
            Function fn3 = actions.iterator().next();

            fn = (v) -> {
                fn1.apply(v);
                fn2.apply(v);
                fn3.apply(v);
                return v;
            };
        } else {
            fn = (v) -> {
                for (Function subFn : actions)
                    subFn.apply(v);

                return v;
            };
        }
    }

    @Override
    public T apply(T o) {
        return fn.apply(o);
    }
}