package pseidon.plugin.pipeline;

import pseidon.plugin.PMessage;
import pseidon.util.Functional;
import pseidon.util.Util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static pseidon.util.Util.asString;

/**
 * Take a sequence of matchers (string-matcher action string-matcher action ... ) <br/>
 * and run the function with the first match, if no match found the identify function is used.<br/>
 *
 * Matches left to right.
 * <br/>
 * string-matcher can be a String or a java.util.Pattern
 */
public class MatchAction implements Function<PMessage, PMessage> {

    final Function<PMessage, PMessage> fn;

    /**
     *
     */
    public MatchAction(Collection<List> actions){

        Iterator<List> it = actions.iterator();

        if(!it.hasNext()) {
            fn = Functional.identity();
        } else {

            fn = Functional.reduce(
                    it,
                    matcherFn(Functional.identity(), it.next()),
                    (continuation, matchPair) -> matcherFn(continuation, matchPair));
        }

    }

    private static final Function<PMessage, PMessage> matcherFn(Function<PMessage, PMessage> continuation, List matchPair){
        if(matchPair.size() != 2)
            throw new RuntimeException("Cannot create match action, must have equal number of match plugin actions");

        return matcherFn(asString(matchPair.get(0)), (Function<PMessage, PMessage>)matchPair.get(1), continuation);
    }

    /**
     * if patternStr is a Pattern a regex match is done, otherwise its casted to a string and equals is used.
     */
    private static final Function<PMessage, PMessage> matcherFn(Object patternStr, Function<PMessage, PMessage> actionFn, Function<PMessage, PMessage> continuation){
        if(patternStr instanceof Pattern){

            Predicate<String> matcher = ((Pattern)patternStr).asPredicate();

            return (PMessage v) -> {
                if(matcher.test(v.getType()))
                    return actionFn.apply(v);
                else
                    //if no match to the next matcher till we reach default
                    return continuation.apply(v);
            };

        }else{

            String str = Util.asString(patternStr);

            return (PMessage v) -> {
                if(v.getType().equals(str))
                    return actionFn.apply(v);
                else
                    return continuation.apply(v);
            };

        }
    }

    @Override
    public PMessage apply(PMessage o) {
        return fn.apply(o);
    }
}
