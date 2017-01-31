# Pseidon Plugins


## Overview

The pseidon-etl service read data from kafka and pass the messages into a plugin pipeline.
A plugin pipeline is the composition of classes implementing the pseidon.plugin.Plugin interface.

The plugin pipeline definition is loaded via an edn script.

Exmaple

```
{
 ;; define plugins as name pluginclass

 a pseidon.plugin.IncPlugin
 b pseidon.plugin.IncPlugin
 c pseidon.plugin.IncPlugin
 d pseidon.plugin.IncPlugin

 ;;define pipeline and refer to plugins via name
 ;;if message is "double" result is 0 -> 4 otherwise 0 -> 3
 pipeline (-> a b (match "double" c) (all d))

 }
```

## Plugin interface


A plugin takes a pseidon.plugin.PMessage and returns a pseidon.plugin.PMessage.

### Example Plugin

```
public class IncPlugin implements Plugin<Integer, Integer>{

    @Override
    public PMessage<Integer> apply(PMessage<Integer> integerMessage) {
        int v = integerMessage.getSingleMessage() + 1;

        System.out.println("IncPlugin:apply: " + integerMessage.getSingleMessage() + " -> " + v);

        return integerMessage.updateMsgs(Arrays.asList(v));
    }
}
```


#### LifeCycle

All plugins defined in the edn file are instantiated and initialised with a context.

```
Plugin plugin = (Plugin)v.newInstance();
plugin.init(ctx);
```

On application shutdown the plugins shutdown method is calls.

## Testing a Pipeline defintion

When developing its useful to test and run the pipeline before deploying.

```
Pipeline<?> fn = PipelineParser.parse(new Context.DefaultCtx(), Reader.read(EDN_FILE));
```