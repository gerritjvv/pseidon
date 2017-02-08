package pseidon.plugin;

import pseidon.util.Functional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class IncBatchedPlugin extends AbstractBatchedPlugin<Integer, Integer>{

    @Override
    public Collection<Integer> exec(String type, Collection<Integer> msgs) {
        List<Integer> resp = new ArrayList<>();

        for(Integer i : msgs){
            resp.add(i+1);
        }

        return resp;
    }
}
