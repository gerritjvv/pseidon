package pseidon.plugin;

import org.junit.Test;
import pseidon.plugin.pipeline.PipelineParser;
import pseidon.plugin.pipeline.Reader;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 */
public class PipelineParserTest {

    private static final String EDN_FILE = "src/test/resources/test-pipeline.edn";

    @Test
    public void testParserRun(){

        /* pipeline definition:

         a pseidon.plugin.IncPlugin
         b pseidon.plugin.IncPlugin
         c pseidon.plugin.IncPlugin
         d pseidon.plugin.IncPlugin

         ;;if message is "double" result is 0 -> 4 otherwise 0 -> 3
         pipeline (-> a b (match "double" c) (all d))
         */

        Pipeline<?> fn = PipelineParser.parse(new Context.DefaultCtx(), Reader.read(EDN_FILE));


        PMessage<Integer> m1 = fn.apply(new PMessage.DefaultPMessage("single", 0));
        PMessage<Integer> m2 = fn.apply(new PMessage.DefaultPMessage("double", 0));

        assertEquals(m1.getSingleMessage().intValue(), 3);

        assertEquals(m2.getSingleMessage().intValue(), 4);

    }
}
