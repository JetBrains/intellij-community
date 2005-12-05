package com.intellij.util.containers;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: lvo
 * Date: Oct 21, 2005
 * Time: 3:23:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class IntObjectCache<T> implements Iterable<T> {

  //private static final Logger LOG = Logger.getInstance("#com.intellij.util.containers.ObjectCache");

  public static final int defaultSize = 8192;
  public static final int minSize = 4;

  protected int myTop;
  protected int myBack;
  final protected CacheEntry<T>[] myCache;
  final protected int[] myHashTable;
  protected int myHashTableSize;
  protected int myCount;
  protected int myFirstFree;

  final ArrayList<DeletedPairsListener> myListeners = new ArrayList<DeletedPairsListener>();

  private static final int[] tableSizes =
    new int[]{5, 11, 23, 47, 101, 199, 397, 797, 1597, 3191, 6397, 12799, 25589, 51199,
      102397, 204793, 409579, 819157, 2295859, 4591721, 9183457, 18366923, 36733847,
      73467739, 146935499, 293871013, 587742049, 1175484103};
  private long myAttempts;
  private long myHits;

  protected static class CacheEntry<T> {
    public int key;
    public T value;
    public int prev;
    public int next;
    public int hash_next;
  }

  public IntObjectCache() {
    this(defaultSize);
  }

  public IntObjectCache(int cacheSize) {
    if (cacheSize < minSize) {
      cacheSize = minSize;
    }
    myTop = myBack = 0;
    myCache = new CacheEntry[cacheSize + 1];
    for (int i = 0; i < myCache.length; ++i) {
      myCache[i] = new CacheEntry<T>();
    }
    myHashTableSize = cacheSize;
    int i = 0;
    for (; myHashTableSize > tableSizes[i];) ++i;
    myHashTableSize = tableSizes[i];
    myHashTable = new int[myHashTableSize];
    myAttempts = 0;
    myHits = 0;
    myCount = myFirstFree = 0;
  }

  // Some AbstractMap functions started

  public boolean isEmpty() {
    return count() == 0;
  }

  public boolean containsKey(int key) {
    return isCached(key);
  }

  public T get(int key) {
    return tryKey(key);
  }

  public T put(int key, T value) {
    T oldValue = tryKey(key);
    if (oldValue != null) {
      remove(key);
    }
    cacheObject(key, value);
    return oldValue;
  }

  public void remove(int key) {
    int index = searchForCacheEntry(key);
    if (index != 0) {
      removeEntry(index);
      removeEntryFromHashTable(index);
      myCache[index].hash_next = myFirstFree;
      myFirstFree = index;
      fireListenersAboutDeletion(index);
      myCache[index].value = null;
    }
  }

  public void removeAll() {
    final IntArrayList keys = new IntArrayList(count());
    for (int current = myTop; current > 0;) {
      if (myCache[current].value != null) {
        keys.add(myCache[current].key);
      }
      current = myCache[current].next;
    }
    for (int i = 0; i < keys.size(); ++ i) {
      remove(keys.get(i));
    }
  }

  // Some AbstractMap functions finished

  final public void cacheObject(int key, T x) {
    int index = myFirstFree;
    if (myCount < myCache.length - 1) {
      if (index == 0) {
        index = myCount;
        ++index;
      }
      else {
        myFirstFree = myCache[index].hash_next;
      }
      if (myCount == 0) {
        myBack = index;
      }
    }
    else {
      index = myBack;
      removeEntryFromHashTable(index);
      fireListenersAboutDeletion(index);
      myCache[myBack = myCache[index].prev].next = 0;
    }
    myCache[index].key = key;
    myCache[index].value = x;
    addEntry2HashTable(index);
    add2Top(index);
  }

  final public T tryKey(int key) {
    ++myAttempts;
    final int index = searchForCacheEntry(key);
    if (index == 0) {
      return null;
    }
    ++myHits;
    final CacheEntry<T> cacheEntry = myCache[index];
    final int top = myTop;
    if (index != top) {
      final int prev = cacheEntry.prev;
      final int next = cacheEntry.next;
      if (index == myBack) {
        myBack = prev;
      }
      else {
        myCache[next].prev = prev;
      }
      myCache[prev].next = next;
      cacheEntry.next = top;
      cacheEntry.prev = 0;
      myCache[top].prev = index;
      myTop = index;
    }
    return cacheEntry.value;
  }

  final public boolean isCached(int key) {
    return searchForCacheEntry(key) != 0;
  }

  public int count() {
    return myCount;
  }

  public int size() {
    return myCache.length - 1;
  }

  public double hitRate() {
    return myAttempts > 0 ? (double)myHits / (double)myAttempts : 0;
  }

  private void add2Top(int index) {
    myCache[index].next = myTop;
    myCache[index].prev = 0;
    myCache[myTop].prev = index;
    myTop = index;
  }

  private void removeEntry(int index) {
    if (index == myBack) {
      myBack = myCache[index].prev;
    }
    else {
      myCache[myCache[index].next].prev = myCache[index].prev;
    }
    if (index == myTop) {
      myTop = myCache[index].next;
    }
    else {
      myCache[myCache[index].prev].next = myCache[index].next;
    }
  }

  private void addEntry2HashTable(int index) {
    int hash_index = (myCache[index].key & 0x7fffffff) % myHashTableSize;
    myCache[index].hash_next = myHashTable[hash_index];
    myHashTable[hash_index] = index;
    ++myCount;
  }

  private void removeEntryFromHashTable(int index) {
    final int hash_index = (myCache[index].key & 0x7fffffff) % myHashTableSize;
    int current = myHashTable[hash_index];
    int previous = 0;
    while (current != 0) {
      int next = myCache[current].hash_next;
      if (current == index) {
        if (previous != 0) {
          myCache[previous].hash_next = next;
        }
        else {
          myHashTable[hash_index] = next;
        }
        --myCount;
        break;
      }
      previous = current;
      current = next;
    }
  }

  private int searchForCacheEntry(int key) {
    myCache[0].key = key;
    int current = myHashTable[((key & 0x7fffffff) % myHashTableSize)];
    for(;;) {
      final CacheEntry<T> cacheEntry = myCache[current];
      if( key == cacheEntry.key) {
        break;
      }
      current = cacheEntry.hash_next;
    }
    return current;
  }

  // start of Iterable implementation

  public Iterator<T> iterator() {
    return new IntObjectCacheIterator(this);
  }

  protected class IntObjectCacheIterator implements Iterator<T> {
    private int myCurrentEntry;

    public IntObjectCacheIterator(IntObjectCache cache) {
      myCurrentEntry = 0;
      cache.myCache[0].next = cache.myTop;
    }

    public boolean hasNext() {
      return (myCurrentEntry = myCache[myCurrentEntry].next) != 0;
    }

    public T next() {
      return myCache[myCurrentEntry].value;
    }

    public void remove() {
      removeEntry(myCache[myCurrentEntry].key);
    }
  }

  // end of Iterable implementation

  // start of listening features

  public interface DeletedPairsListener extends EventListener {
    void objectRemoved(int key, Object value);
  }

  public void addDeletedPairsListener(DeletedPairsListener listener) {
    myListeners.add(listener);
  }

  public void removeDeletedPairsListener(DeletedPairsListener listener) {
    myListeners.remove(listener);
  }

  private void fireListenersAboutDeletion(int index) {
    final CacheEntry cacheEntry = myCache[index];
    for (int i = 0; i < myListeners.size(); i++) {
      myListeners.get(i).objectRemoved(cacheEntry.key, cacheEntry.value);
    }
  }

  // end of listening features
}
