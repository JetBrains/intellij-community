/*
 * @author max
 */
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

public abstract class SLRUCache<K, V> extends SLRUMap<K,V> {
  protected SLRUCache(final int protectedQueueSize, final int probationalQueueSize) {
    super(protectedQueueSize, probationalQueueSize);
  }

  @NotNull
  public abstract V createValue(K key);

  @NotNull
  public V get(K key) {
    V value = super.get(key);
    if (value != null) {
      return value;
    }

    value = createValue(key);
    put(getStableKey(key), value);

    return value;
  }

}