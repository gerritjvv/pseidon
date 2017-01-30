package pseidon.plugin.pipeline;

import pseidon.plugin.Context;
import pseidon.plugin.Message;
import pseidon.plugin.Plugin;
import pseidon.util.Functional;
import pseidon.util.Util;

import java.util.*;
import java.util.function.Function;

/**
 */
public class PipelineParser {

    public static final Function<Message, Message> parse(Context ctx, Map<String, Object> m){

        Map<String, Plugin> mappings = initPlugins(ctx, Reader.getMappings(m));

        Collection pipelineDef = Reader.getPipeline(m);

        return parse(mappings, pipelineDef.iterator());
    }

    private static Function<Message,Message> parse(Map<String, Plugin> mappings, Object v) {

        if(v instanceof Iterator){

            Iterator it = (Iterator)v;

            if(it.hasNext()){

                String action = Util.asString(it.next());

                switch (action){
                    case "all":
                        return new AllAction(Functional.map(it, x -> parse(mappings, x)));
                    case "->":
                        return new ComposedAction(Functional.map(it, x -> parse(mappings, x)));
                    case "match":

                        return new MatchAction(parseMatcherActions(mappings, it));
                    default:
                        throw new RuntimeException(String.format("%s must be one of all, match or ->"));
                }


            }else{
                return Functional.identity();
            }
        }else{
            return lookup(mappings, Util.asString(v));
        }
    }

    private static Collection<List> parseMatcherActions(Map<String, Plugin> mappings, Iterator it) {
        return Functional.reduce(
                        Util.reverse(Functional.partitionAll(it, 2)),
                        new ArrayList<>(),
                        (List l, List item) -> {
                            //expect item = [matchStringOrPattern PluginOrList]
                            if(item.size() != 2)
                                throw new RuntimeException("Cannot create match action, must have equal number of match plugin actions");

                            l.add(Arrays.asList(item.get(0), parse(mappings, item.get(1))));
                            return l;
                        });
    }

    private static Plugin lookup(Map<String, Plugin> mappings, String name){
        Plugin plugin = mappings.get(name);

        if(plugin == null)
            throw new RuntimeException(String.format("Plugin not found %s ", name));

        return plugin;
    }

    private static Map<String,Plugin> initPlugins(Context ctx, Map<String, Class<?>> mappings) {
        return Functional.reduceKV(
                mappings,
                new HashMap<>(),
                (m, k, v) -> {
                    m.put(k, initPlugin(ctx, v));
                return m;});
    }

    private static Plugin initPlugin(Context ctx, Class<?> v) {
        try {
            Plugin plugin = (Plugin)v.newInstance();
            plugin.init(ctx);

            return plugin;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
