package pseidon.plugin;

import org.junit.Test;
import us.bpsm.edn.Tag;
import us.bpsm.edn.parser.Parseable;
import us.bpsm.edn.parser.Parser;
import us.bpsm.edn.parser.Parsers;
import us.bpsm.edn.parser.TagHandler;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

import static us.bpsm.edn.parser.Parsers.defaultConfiguration;

/**
 */
public class ReaderTest {

    @Test
    public void testReader(){
        Parseable pbr = Parsers.newParseable("{\"a\" #r\"abc\" \"c\" \"123\"}");

        Parser.Config cfg =
                Parsers.newParserConfigBuilder()
                        .putTagHandler(Tag.newTag("r"),
                                (tag, value) -> Pattern.compile(value.toString())).build();
        Parser p = Parsers.newParser(cfg);

        Map m = (Map)p.nextValue(pbr);

        System.out.println("OUTPUT: " + m);
        System.out.println("OUTPUT: " + m.get("a"));



    }
}
