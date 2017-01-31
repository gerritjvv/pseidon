package pseidon.util;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Functional programming utilities
 */
public class Functional {


    public static final <T> Function<T, T> identity()
    {
        return t -> t;
    }

    public static final Iterator<Integer> range(int start, int end){
       return new Iterator<Integer>() {
           int i = start;
           @Override
           public boolean hasNext() {
               return i < end;
           }

           @Override
           public Integer next() {
               return i++;
           }
       };
    }

    public static final <T> List<T> repeatList(int n, Supplier<T> sup){
        List<T> list = new ArrayList<>(n);
        for(int i = 0; i < n; i++)
            list.add(sup.get());

        return list;
    }

    public static final <T,R> Collection<R> map(Iterator<T> it, Function<T, R> fn){
        Iterable f = () -> it;

        return (Collection<R>)
                StreamSupport
                .<T>stream(f.spliterator(), false)
                .map(fn)
                .collect(Collectors.toList());
    }

    public static final <T> List<List<T>> partitionAll(Iterator<T> it, int n){
        Iterable<T> f = () -> it;

        return partitionAll(StreamSupport.stream(f.spliterator(), false).collect(Collectors.toList()), n);
    }
    /**
     * Same as https://clojuredocs.org/clojure.core/partition-all
     */
    public static final <T> List<List<T>> partitionAll(Collection<T> coll, int n){
        List<List<T>> partitions = new ArrayList<>(coll.size());

        int index = 0;

        List<T> subList = new ArrayList<>(n);

        for(T item : coll){

            subList.add(item);

            if(++index % n == 0){
                partitions.add(subList);
                subList = new ArrayList<>(n);
            }
        }

        if(!subList.isEmpty())
            partitions.add(subList);

        return partitions;
    }

    public static final <INPUT, STATE> STATE reduce(Iterator<INPUT> input, STATE init, BiFunction<STATE, INPUT, STATE> fn)
    {
        STATE state = init;

        while(input.hasNext())
            state = fn.apply(state, input.next());

        return state;
    }

    public static final <INPUT, STATE> STATE reduce(Collection<INPUT> input, STATE init, BiFunction<STATE, INPUT, STATE> fn)
    {
        STATE state = init;
        for(INPUT item : input)
            state = fn.apply(state, item);

        return state;
    }

    public static final <K, V, STATE> STATE reduceKV(Map<K, V> m, STATE init, KVReducerFN<STATE, K, V> fn){
        STATE state = init;

        for(Map.Entry<K, V> entry : m.entrySet())
            state = fn.apply(state, entry.getKey(), entry.getValue());

        return state;
    }


    @FunctionalInterface
    public interface KVReducerFN<STATE, K, V>{
        public STATE apply(STATE state, K k, V v);
    }
}
