package pseidon.plugin;

import org.junit.Test;
import pseidon.plugin.pipeline.ComposedAction;
import pseidon.util.Functional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ComposedActionTest extends AbstractionActionTest {


    @Test
    public void testComposed1() {

        List<Integer> results = new ArrayList<>();

        new ComposedAction(Arrays.asList(incMsg(results)))
                .apply(new PMessage.DefaultPMessage("test", Arrays.asList(0)));

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).intValue(), 0);

    }

    @Test
    public void testComposedN() {

        int n = new Random().nextInt(100) * 100;

        List<Integer> results = new ArrayList<>();

        List<Function<PMessage, PMessage>> callFunctions = Functional.reduce(
                Functional.range(0, n),
                new ArrayList<>(),

                (List list, Integer i) -> {

                    list.add(incMsg(results));
                    return list;
                });


        new ComposedAction(callFunctions).apply(PMessage.instance("test", Arrays.asList(0)));

        for(int i = 0; i < n; i++)
            assertEquals(i, results.get(i).intValue());
    }

    private Function<PMessage, PMessage> incMsg(List<Integer> results) {
        return (PMessage msg) -> {
            Integer v = (Integer)msg.getMessages().iterator().next();
            results.add(v);
            return msg.updateMsgs(Arrays.asList(v+1));
        };
    }
}
