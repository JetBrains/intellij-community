/*
 * @author max
 */
package com.intellij.util.containers;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class SLRUMap<K,V> {
  private final Map<K,V> myProtectedQueue;
  private final Map<K,V> myProbationalQueue;

  private final int myProtectedQueueSize;
  private final int myProbationalQueueSize;

  private int probationalHits = 0;
  private int protectedHits = 0;
  private int misses = 0;

  public SLRUMap(final int protectedQueueSize, final int probationalQueueSize) {
    myProtectedQueueSize = protectedQueueSize;
    myProbationalQueueSize = probationalQueueSize;

    myProtectedQueue = new LinkedHashMap<K,V>(protectedQueueSize, 0.6f, true) {
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        if (size() > myProtectedQueueSize) {
          myProbationalQueue.put(eldest.getKey(), eldest.getValue());
          return true;
        }

        return false;
      }
    };

    myProbationalQueue = new LinkedHashMap<K,V>(probationalQueueSize, 0.6f, true) {
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        if (size() > myProbationalQueueSize) {
          onDropFromCache(eldest.getKey(), eldest.getValue());
          return true;
        }
        return false;
      }
    };
  }

  @Nullable
  public V get(K key) {
    V value = myProtectedQueue.get(key);
    if (value != null) {
      protectedHits++;
      return value;
    }

    value = myProbationalQueue.remove(key);
    if (value != null) {
      probationalHits++;
      myProtectedQueue.put(getStableKey(key), value);
      return value;
    }

    misses++;
    return null;
  }

  public void put(K key, V value) {
    V oldValue = myProtectedQueue.remove(key);
    if (oldValue != null) {
      onDropFromCache(key, value);
    }

    oldValue = myProbationalQueue.put(getStableKey(key), value);
    if (oldValue != null) {
      onDropFromCache(key, value);
    }
  }

  protected void onDropFromCache(K key, V value) {}

  public boolean remove(K key) {
    V value = myProtectedQueue.remove(key);
    if (value != null) {
      onDropFromCache(key, value);
      return true;
    }

    value = myProbationalQueue.remove(key);
    if (value != null) {
      onDropFromCache(key, value);
      return true;
    }

    return false;
  }

  public void clear() {
    for (Map.Entry<K, V> entry : myProtectedQueue.entrySet()) {
      onDropFromCache(entry.getKey(), entry.getValue());
    }
    myProtectedQueue.clear();

    for (Map.Entry<K, V> entry : myProbationalQueue.entrySet()) {
      onDropFromCache(entry.getKey(), entry.getValue());
    }
    myProbationalQueue.clear();
  }

  protected K getStableKey(K key) {
    if (key instanceof ShareableKey) {
      return (K)((ShareableKey)key).getStableCopy();
    }

    return key;
  }
}