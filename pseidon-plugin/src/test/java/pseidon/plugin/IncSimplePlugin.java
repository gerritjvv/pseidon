package pseidon.plugin;

import java.util.Arrays;

/**
 *
 */
public class IncSimplePlugin extends AbstractPlugin<Integer, Integer>{

    @Override
    public Integer exec(String type, Integer msg) {
        return msg + 1;
    }
}
