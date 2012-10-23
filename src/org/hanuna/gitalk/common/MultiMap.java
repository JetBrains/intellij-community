package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface MultiMap<K, V> {

    public void add(K key, V value);

    @NotNull
    public ReadOnlyList<V> get(K key);
}
