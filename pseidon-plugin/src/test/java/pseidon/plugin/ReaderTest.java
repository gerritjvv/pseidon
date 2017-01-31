package pseidon.plugin;

import org.junit.Test;
import pseidon.plugin.pipeline.Reader;
import us.bpsm.edn.Symbol;

import java.util.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 */
public class ReaderTest {

    private static final String EDN_FILE = "src/test/resources/test-pipeline.edn";

    @Test
    public void testRead() {

        Map<String, Object> m = Reader.read(EDN_FILE);

        System.out.println("returned m " + m);
        Collection pipeline = Reader.getPipeline(m);
        Map<String, Class<?>> mappings = Reader.getMappings(m);

        System.out.println("pipeline: " + pipeline);
        System.out.println("mappings: " + mappings);


        assertTrue(
                isEquals(
                        new ArrayList(pipeline),
                        Arrays.asList(
                                Symbol.newSymbol("->"),
                                Symbol.newSymbol("a"),
                                Symbol.newSymbol("b"),
                                Arrays.asList(Symbol.newSymbol("match"), "double", Symbol.newSymbol("c")),
                                Arrays.asList(Symbol.newSymbol("all"), Symbol.newSymbol("d")))));

        assertTrue(mappings.containsKey("a"));
        assertTrue(mappings.containsKey("b"));
        assertTrue(mappings.containsKey("c"));
        assertTrue(mappings.containsKey("d"));
    }

    private boolean isEquals(List l1, List l2){

        if(l1.size() != l2.size())
            return false;

        for(int i = 0; i < l1.size(); i++){
            Object o1 = l1.get(i);
            Object o2 = l2.get(i);

            if(o1 instanceof List){
                if(o2 instanceof List){
                    if(!isEquals((List)o1, (List)o2))
                        return false;
                }
                else
                    return false;
            }else{
                if(!o1.equals(o2))
                    return false;
            }
        }

        return true;
    }

}
