package pseidon_etl;

import clojure.lang.IFn;
import pseidon.plugin.PMessage;
import pseidon.plugin.Plugin;

/**
 * Wraps a clojure IFn as a plugin, the return value of the function is discarded
 */
public class IFnPlugin implements Plugin<PMessage, PMessage>{

    private final IFn fn;

    public IFnPlugin(IFn fn) {
        this.fn = fn;
    }

    @Override
    public PMessage<PMessage> apply(PMessage<PMessage> pMessage) {

        fn.invoke(pMessage);

        return pMessage;
    }
}
