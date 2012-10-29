package org.hanuna.gitalk.common;

import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 */
public class CacheGet<K, V> implements Get<K, V>{
    private final Get<K, V> getFunction;
    private final int size;
    private Map<K, V> map;
    private Map<K, V> moreMap;

    public CacheGet(Get<K, V> getFunction, int size) {
        this.getFunction = getFunction;
        this.size = size;
        this.map = new HashMap<K, V>(2 * size);
        this.moreMap = new HashMap<K, V>(2 * size);
    }

    @Override
    public V get(K key) {
        V value = moreMap.get(key);
        if (value != null) {
            return value;
        } else {
            value = getFunction.get(key);
            moreMap.put(key, value);
            map.put(key, value);
            checkSize();
            return value;
        }
    }

    private void checkSize() {
        if (map.size() >= size) {
            Map<K, V> tempMap = moreMap;
            moreMap = map;
            map = tempMap;
            map.clear();
        }
    }
}
