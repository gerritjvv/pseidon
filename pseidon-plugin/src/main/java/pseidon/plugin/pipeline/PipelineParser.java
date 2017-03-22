package pseidon.plugin.pipeline;

import pseidon.plugin.Context;
import pseidon.plugin.PMessage;
import pseidon.plugin.Pipeline;
import pseidon.plugin.Plugin;
import pseidon.util.Functional;
import pseidon.util.Util;

import java.util.*;
import java.util.function.Function;

/**
 * Parses an edn plugin definition<br/>
 * initiates all {@link Plugin}(s) defined,<br/>
 * and create a function <code>Function<PMessage,PMessage></code> that defines the pipeline.<br/>
 * <p/>
 */
public class PipelineParser {

    /**
     * @param ctx {@link Context}
     * @param m   Must be from {@link Reader#read(String)}
     * @return
     */
    public static final <T> Pipeline<T> parse(Context ctx, Map<String, Object> m) {

        Map<String, Plugin> mappings = initPlugins(ctx, Reader.getMappings(m));

        Collection pipelineDef = Reader.getPipeline(m);

        return new DefaultPipeline<>(
                mappings,
                parse(ctx, mappings, pipelineDef.iterator())
        );
    }

    /**
     * @param mappings {@link Reader#read(String)} and {@link Reader#getMappings(Map)}
     * @param v        {@link Reader#read(String)} and {@link Reader#getPipeline(Map)}
     * @return
     */
    private static Function<PMessage, PMessage> parse(Context ctx, Map<String, Plugin> mappings, Object v) {

        if (v instanceof Iterator || v instanceof Collection) {

            Iterator it = (v instanceof Iterator) ? (Iterator) v : ((Collection) v).iterator();

            if (it.hasNext()) {

                String action = Util.asString(it.next());

                switch (action) {
                    case "all":
                        return new AllAction(Functional.map(it, x -> parse(ctx, mappings, x)));
                    case "->":
                        return new ComposedAction(Functional.map(it, x -> parse(ctx, mappings, x)));
                    case "match":
                        return new MatchAction(parseMatcherActions(ctx, mappings, it));
                    default:
                        throw new RuntimeException(String.format("%s must be one of all, match or ->", action));
                }


            } else {
                return Functional.identity();
            }
        } else {
            return lookup(ctx, mappings, Util.asString(v));
        }
    }

    private static Collection<List> parseMatcherActions(Context ctx, Map<String, Plugin> mappings, Iterator it) {

        return Functional.reduce(
                Util.reverse(Functional.partitionAll(it, 2)),
                new ArrayList<>(),
                (List l, List item) -> {
                    //expect item = [matchStringOrPattern PluginOrList]
                    System.out.println("item: " + item);
                    if (item.size() != 2)
                        throw new RuntimeException("Cannot create match action, must have equal number of match plugin actions");

                    l.add(Arrays.asList(item.get(0), parse(ctx, mappings, item.get(1))));
                    return l;
                });
    }

    /**
     * Lookup the plugin from mappings.
     */
    private static Plugin lookup(Context context, Map<String, Plugin> mappings, String name) {
        Plugin plugin = mappings.get(name);

        if (plugin == null)
            plugin = context.getPlugin(name);

        if (plugin == null)
            throw new RuntimeException(String.format("Plugin not found %s ", name));

        return plugin;
    }

    /**
     * Startup all the defined {@link Plugin}(s) and call {@link Plugin#init(Context)}
     *
     * @param ctx
     * @param mappings
     * @return
     */
    private static Map<String, Plugin> initPlugins(Context ctx, Map<String, Class<?>> mappings) {
        return Functional.reduceKV(
                mappings,
                new HashMap<>(),
                (m, k, v) -> {
                    m.put(k, initPlugin(ctx, v));
                    return m;
                });
    }

    private static Plugin initPlugin(Context ctx, Class<?> v) {
        try {
            Plugin plugin = (Plugin) v.newInstance();
            plugin.init(ctx);

            return plugin;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
