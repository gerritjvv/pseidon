package pseidon.plugin;

import java.util.Collection;

/**
 */
public interface Message<T> {

    String getType();
    Collection<T> msgs();

    <R> Message<R> updateMsgs(Collection<R> msgs);
    Message<T> updateMsgs(String type);

    class DefaultMessage<T> implements Message<T>{

        private final String type;
        private final Collection<T> msgs;

        public DefaultMessage(String type, Collection<T> msgs) {
            this.type = type;
            this.msgs = msgs;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Collection<T> msgs() {
            return msgs;
        }

        @Override
        public <R> Message<R> updateMsgs(Collection<R> msgs) {
            return new DefaultMessage<R>(type, msgs);
        }

        @Override
        public Message<T> updateMsgs(String type) {
            return new DefaultMessage<T>(type, msgs);
        }
    }
}
