package pseidon.plugin;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.Assert.assertNotNull;

/**
 */
public class AbstractionActionTest {

    public Function<PMessage, PMessage> createFn(AtomicBoolean v) {
        return (PMessage s) -> {
            assertNotNull(s);
            v.set(true);
            return null;
        };
    }


    public static final PMessage<String> testMessage() {
        return new PMessage.DefaultPMessage<>("abc", Arrays.asList("data"));
    }
}
