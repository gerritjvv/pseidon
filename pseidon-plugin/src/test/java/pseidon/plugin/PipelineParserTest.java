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

    private static final String BATCHED_EDN_FILE = "src/test/resources/test-batched-inc-pipeline.edn";

    private static final String SIMPLE_EDN_FILE = "src/test/resources/test-simple-inc-pipeline.edn";

    @Test
    public void testIncPlugin(){
        parserRun(EDN_FILE);
    }

    @Test
    public void testBatchedIncPlugin(){
        parserRun(SIMPLE_EDN_FILE);
    }

    @Test
    public void testSimpleIncPlugin(){
        parserRun(BATCHED_EDN_FILE);
    }

    public void parserRun(String ednFile){

        Pipeline<Integer> fn = PipelineParser.parse(Context.instance(), Reader.read(ednFile));

        PMessage<Integer> m1 = fn.apply(PMessage.instance("single", 0));
        PMessage<Integer> m2 = fn.apply(PMessage.instance("double", 0));

        assertEquals(m1.getSingleMessage().intValue(), 3);

        assertEquals(m2.getSingleMessage().intValue(), 4);

    }
}
