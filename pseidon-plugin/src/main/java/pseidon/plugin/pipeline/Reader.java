package pseidon.plugin.pipeline;

import pseidon.util.Functional;
import us.bpsm.edn.Tag;
import us.bpsm.edn.parser.Parseable;
import us.bpsm.edn.parser.Parser;
import us.bpsm.edn.parser.Parsers;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static pseidon.util.Util.asPluginClass;
import static pseidon.util.Util.asString;
import static us.bpsm.edn.Symbol.newSymbol;

/**
 * Read the pipeline configuration file<br/>
 * <p/>
 * Custom Supported Types:<br/>
 * <ul><li>#r"regex-string" creates a Pattern object that matches "regex-string"</li></ul>
 *
 */
public class Reader {


    /**
     * Return the key class mappings, the current context class loader is used.
     * @param conf
     * @return
     */
    public static Map<String, Class<?>> getMappings(Map<?, ?> conf)
    {
        return Functional.reduceKV(
                conf,
                new HashMap<>(),
                (m, k, v) -> {
                    m.put(
                            asString(k),
                            asPluginClass(asString(v)));
                    return m;
                });
    }

    public static Collection getPipeline(Map<?, ?> conf){
        return (Collection) conf.get(newSymbol("pipeline"));
    }

    /**
     * Reader supports regex java.util.Pattern or Strings.
     */
    public static final Map<String, Object> read(String ednFile) {
        try {

            Parseable pbr = Parsers.newParseable(new String(Files.readAllBytes(Paths.get(ednFile)) ,"UTF-8"));

            Parser.Config cfg =
                    Parsers.newParserConfigBuilder()
                            .putTagHandler(Tag.newTag("r"),
                                    (tag, value) -> Pattern.compile(value.toString())).build();

            Parser p = Parsers.newParser(cfg);

            return Functional.reduceKV(
                    (Map<?, ?>) p.nextValue(pbr),
                    new HashMap<>(),

                    (m, k, v) -> {
                        m.put(asString(k), asString(v));
                    return m;});

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
