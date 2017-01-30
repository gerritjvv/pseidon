package pseidon.plugin;

import org.junit.Test;
import pseidon.plugin.pipeline.AllAction;
import pseidon.util.Functional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class AllActionTest extends AbstractionActionTest{

    @Test
    public void testAllAction1() {

        AtomicBoolean called = new AtomicBoolean(true);
        AllAction allAction = new AllAction(Arrays.asList(createFn(called)));

        allAction.apply(testMessage());

        assertTrue(called.get());
    }

    @Test
    public void testAllAction2() {


        AtomicBoolean called1 = new AtomicBoolean(true);
        AtomicBoolean called2 = new AtomicBoolean(true);

        AllAction allAction = new AllAction(Arrays.asList(
                createFn(called1),
                createFn(called2)
        ));

        allAction.apply(testMessage());

        assertTrue(called1.get());
        assertTrue(called2.get());

    }

    @Test
    public void testAllAction3() {


        AtomicBoolean called1 = new AtomicBoolean(true);
        AtomicBoolean called2 = new AtomicBoolean(true);
        AtomicBoolean called3 = new AtomicBoolean(true);

        AllAction allAction = new AllAction(Arrays.asList(
                createFn(called1),
                createFn(called2),
                createFn(called3)
        ));

        allAction.apply(testMessage());

        assertTrue(called1.get());
        assertTrue(called2.get());
        assertTrue(called3.get());
    }

    @Test
    public void testAllActionN() {


        int n = new Random().nextInt(100) + 5;

        List<AtomicBoolean> callFlags = Functional.repeatList(n, () -> new AtomicBoolean());

        List<?> callFunctions = Functional.reduce(
                Functional.range(0, n),
                new ArrayList<>(),
                (List list, Integer i) -> {

                    list.add(createFn(callFlags.get(i)));

                    return list;
                });


        new AllAction(callFunctions).apply(testMessage());

        for(AtomicBoolean b: callFlags)
            assertTrue(b.get());
    }
}
