package pseidon.util;

import pseidon.plugin.Plugin;
import us.bpsm.edn.Named;

import java.util.*;
import java.util.stream.Stream;

/**
 * Simple utility functions
 */
public class Util {

    public static final <T> Collection<T> reverse(Collection<T> coll){
        List<T> reverseList = new ArrayList<T>(coll.size());

        int index = coll.size()-1;

        for(T item : coll)
            reverseList.set(index--, item);

        return reverseList;
    }

    public static Class<Plugin> asPluginClass(String cls){
        try{
            return (Class<Plugin>)Class.forName(cls);
        }catch (ClassNotFoundException e1){
            try{
                return (Class<Plugin>)Thread.currentThread().getContextClassLoader().loadClass(cls);
            }catch (ClassNotFoundException e2){
                try {
                    return (Class<Plugin>)cls.getClass().forName(cls);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(String.format("Plugin class %s not found", cls));
                }
            }
        }
    }

    public static String asString(Object o) {
        if(o == null)
            return "";
        else if(o instanceof Named)
            return ((Named)o).getName();
        else if(o instanceof String[]){
            return String.join(", ", (String[])o);
        }
        else if(o instanceof Collection){
            return String.join(", ", (Collection)o);
        }
        else
            return o.toString();
    }
}
