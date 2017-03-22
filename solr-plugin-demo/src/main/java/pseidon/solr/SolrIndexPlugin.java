package pseidon.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import pseidon.plugin.AbstractPlugin;
import pseidon.plugin.Context;
import pseidon.plugin.PMessage;
import pseidon.plugin.TopicMsg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * An example of writing a pseidon plugin using SOLR indexing.
 */
public class SolrIndexPlugin extends AbstractPlugin<PMessage<TopicMsg>, PMessage<TopicMsg>> {

    SolrClient solr;

    /**
     * Glogal configuration and state init can be done here.
     *
     * @param ctx
     */
    @Override
    public void init(Context ctx) {
        solr = new HttpSolrClient(ctx.getConf("solr-url"));
    }

    @Override
    public void shutdown() {
        if (solr != null) {
            try {
                solr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This method is called for every N messages.
     */
    @Override
    public PMessage<TopicMsg> exec(String s, PMessage<TopicMsg> o) {

        //transform the PMessage(s) into SolrInputDocument(s)
        Collection<SolrInputDocument> docs = o.getMessages()
                .stream()
                .map(m -> createDoc(m))
                .collect(Collectors.toCollection(() -> new ArrayList<>()));

        //save to solr
        try {
            solr.add(docs);
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return o;
    }

    /**
     * Only supports "txt" messages where the object message is a String array.<br/>
     * We read only the fields index of the message and add to searchField.<br/>
     * This is just a demo, a production plugin will know which fields map to which indexes.
     *
     * @param topicMsg
     * @return
     */
    private SolrInputDocument createDoc(TopicMsg topicMsg) {
        if (!topicMsg.getCodec().equalsIgnoreCase("txt"))
            throw new RuntimeException("Only txt messages are expected");

        String[] txt = ((String[]) topicMsg.getMsg().getMsg());

        SolrInputDocument document = new SolrInputDocument();
        document.addField("searchField", txt[0]);

        return document;
    }
}
