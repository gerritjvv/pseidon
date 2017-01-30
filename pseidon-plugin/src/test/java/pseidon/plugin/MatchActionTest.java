package pseidon.plugin;

import org.junit.Test;
import pseidon.plugin.pipeline.MatchAction;
import pseidon.util.Functional;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 */
public class MatchActionTest {


    @Test
    public void testPatternMatch(){
        testMatch(Pattern::compile);
    }


    @Test
    public void testStringMatch(){
        testMatch(Functional.identity());
    }

    private  <T> void testMatch(Function<String, T> converter){

        Random rand = new Random();

        int n = rand.nextInt(100) * 10;

        List<String> ids = new ArrayList<>();

        List<T> vals = new ArrayList<>();

        List<List> matchPairs = Functional.repeatList(n, () -> {
            String id = String.valueOf(rand.nextInt());
            ids.add(id);

            T val = converter.apply(id);
            vals.add(val);

            return matchedPair(val);
        });

        MatchAction matchAction = new MatchAction(matchPairs);


        for(int i = 0; i < ids.size(); i++){
            String id = ids.get(i);
            T val = vals.get(i);

            assertEquals(
                    matchAction.apply(new Message.DefaultMessage(id, null))
                            .msgs().iterator().next(),
                    val);
        }

    }


    private <T> List matchedPair(T match){
        return Arrays.asList(match, matchedFn(match));
    }

    private <T> Function<Message, Message> matchedFn(T id){
        return m -> m.updateMsgs(Arrays.asList(id));
    }

}
