package pseidon.plugin;

import java.util.Arrays;

/**
 * Plugin that takes a PMessage[getMessages[integer]] and increments it
 */
public class IncPlugin implements Plugin<Integer, Integer>{

    @Override
    public PMessage<Integer> apply(PMessage<Integer> integerMessage) {
        int v = integerMessage.getSingleMessage() + 1;

        System.out.println("IncPlugin:apply: " + integerMessage.getSingleMessage() + " -> " + v);

        return integerMessage.updateMsgs(Arrays.asList(v));
    }
}
