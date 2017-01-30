package pseidon.plugin;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.Assert.assertNotNull;

/**
 */
public class AbstractionActionTest {

    public Function<Message, Message> createFn(AtomicBoolean v) {
        return (Message s) -> {
            assertNotNull(s);
            v.set(true);
            return null;
        };
    }


    public static final Message<String> testMessage() {
        return new Message.DefaultMessage<>("abc", Arrays.asList("data"));
    }
}
