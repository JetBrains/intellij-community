package distinct;

import one.util.streamex.EntryStream;
import java.util.*;

public class EntryWithSideEffectsDistinctValues {
    public static void main(String[] args) {
        // Breakpoint! lambdaOrdinal(-1)
        EntryStream.of(new PrintingMap().put(10, 1).put(11, 1).put(20, 2).put(30, 3))
            .distinctValues()
            .count();
    }

    private static class PrintingMap extends AbstractMap<Integer, Integer> {
        private final List<Entry<Integer, Integer>> entries = new ArrayList<>();

        PrintingMap put(int k, int v) {
            entries.add(new SimpleEntry<>(k, v) {
                @Override public Integer getKey() {
                    Integer key = super.getKey();
                    System.out.println("getKey: " + key);
                    return key;
                }
                @Override public Integer getValue() {
                    Integer value = super.getValue();
                    System.out.println("getValue: " + value);
                    return value;
                }
            });
            return this;
        }

        @Override
        public Set<Entry<Integer, Integer>> entrySet() {
            return new AbstractSet<>() {
                @Override public Iterator<Entry<Integer, Integer>> iterator() { return entries.iterator(); }
                @Override public int size() { return entries.size(); }
            };
        }
    }
}
