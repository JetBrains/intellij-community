package org.hanuna.gitalk.common;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.common.readonly.SimpleReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author erokhins
 */
public class HashMultiMap<K, V> implements MultiMap<K, V> {
    private final Map<K, List<V>> map = new HashMap<K, List<V>>();
    private final ReadOnlyList<V> emptyList = SimpleReadOnlyList.getEmpty();

    @Override
    public void add(K key, V value) {
        List<V> elements = map.get(key);
        if (elements == null) {
            elements = new ArrayList<V>(2);
            map.put(key, elements);
        }
        elements.add(value);
    }

    @NotNull
    @Override
    public ReadOnlyList<V> get(K key) {
        List<V> elements = map.get(key);
        if (elements == null) {
            return emptyList;
        } else {
            return new SimpleReadOnlyList<V>(elements);
        }
    }
}
