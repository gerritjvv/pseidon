package pseidon.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * P(lugin)Message(s) passed to and returned from {@link Plugin}s.<br/>
 * <p/>
 * The input message from pseidon-etl is TopicMsg.
 */
public interface PMessage<T> {

    /**
     * Each message can be a assigned a "Type" string which can have different meanings:<br/>
     * E.g in a kafka streaming system this might mean a log topic.<br/>
     * Another system might use it to type tag messages for disk or db persistence etc.<br/>
     * <p/>
     *
     * Types and subtypes are dependent on the plugins and implementation. <br/>
     * E.g a subtype schema can use the ':' or '/' character to define a hierarchy "mylogs/specificlog" .
     * @return
     */
    String getType();

    /**
     * payloads inside the message can be batched, thus we use a collection here.
     */
    Collection<T> getMessages();

    /**
     * When only a single message is expected or required, if no messages return null.
     */
    T getSingleMessage();

    /**
     * Messages are immutable, to return a new PMessage of the same type but with different messages.
     */
    <R> PMessage<R> updateMsgs(Collection<R> msgs);

    /**
     * PMessage types are immutable, to return a new PMessage with the same messages but with a different type.
     */

    PMessage<T> updateMsgs(String type);

    static <T> PMessage<T> instance(String type, T msg)
    {
        return new DefaultPMessage<T>(type, msg);
    }

    static <T> PMessage<T> instance(String type, Collection<T> msgs)
    {
        return new DefaultPMessage<T>(type, msgs);
    }

    class DefaultPMessage<T> implements PMessage<T> {

        private final String type;
        private final Collection<T> msgs;

        public DefaultPMessage(String type, T msg){
            this(type, Collections.singletonList(Objects.requireNonNull(msg)));
        }

        public DefaultPMessage(String type, Collection<T> msgs) {
            this.type = Objects.requireNonNull(type);
            this.msgs = Collections.unmodifiableCollection(msgs);
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Collection<T> getMessages() {
            return msgs;
        }

        @Override
        public T getSingleMessage() {
          return msgs.isEmpty() ? null : msgs.iterator().next();
        }

        @Override
        public <R> PMessage<R> updateMsgs(Collection<R> msgs) {
            return new DefaultPMessage<R>(type, msgs);
        }

        @Override
        public PMessage<T> updateMsgs(String type) {
            return new DefaultPMessage<T>(type, msgs);
        }
    }
}
