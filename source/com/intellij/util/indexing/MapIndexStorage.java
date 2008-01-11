package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ObjectCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 20, 2007
*/
final class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapIndexStorage");
  private final PersistentHashMap<Key, ValueContainerImpl<Value>> myMap;
  private final ObjectCache<Key, ValueContainerImpl<Value>> myCache;
  private Key myKeyBeingRemoved = null;
  private Set<Key> myFlushedKeys = null;
  
  //private final LinkedHashMap<Key, ValueContainerImpl<Value>> myUnsavedItems = new LinkedHashMap<Key, ValueContainerImpl<Value>>();
  //private final Object myPersistenceLock = new Object();

  // todo: for debugging only:
  //private final HashSet<Key> myUniqueKeys = new HashSet<Key>();
  
  public MapIndexStorage(
    File storageFile, 
    final PersistentEnumerator.DataDescriptor<Key> keyDescriptor, 
    final DataExternalizer<Value> valueExternalizer) throws IOException {

    myMap = new PersistentHashMap<Key,ValueContainerImpl<Value>>(storageFile, keyDescriptor, new ValueContainerExternalizer<Value>(valueExternalizer));
    myCache = new ObjectCache<Key, ValueContainerImpl<Value>>(1024);
    myCache.addDeletedPairsListener(new ObjectCache.DeletedPairsListener() {
      public void objectRemoved(final Object key, final Object value) {
        final Key _key = (Key)key;
        if (myCache.isCached(_key) || _key.equals(myKeyBeingRemoved)) {
          // If the cache still contains the key, the value we see in this listener is just an outdated value.
          // No need to save it, because the cache contains the most recent data for the key.
          return;
        }
        if (myFlushedKeys != null) {
          if (myFlushedKeys.contains(_key)) {
            return; // already flushed
          }
          myFlushedKeys.add(_key);
        }
        
        try {
          //myUniqueKeys.add(_key); // todo: for debugging only!!
          //noinspection unchecked
          myMap.put(_key, (ValueContainerImpl<Value>)value);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }
  
  public void flush() {
    //System.out.println("Cache hit rate = " + myCache.hitRate());
    myFlushedKeys = new HashSet<Key>();
    try {
      myCache.removeAll();
    }
    finally {
      myFlushedKeys = null;
    }
    //System.out.println("Found " + myUniqueKeys.size() + " unique keys");
    //myUniqueKeys.clear();
  }
  
  public void close() throws StorageException {
    try {
      flush();
      myMap.close();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  
  @NotNull
  public ValueContainer<Value> read(final Key key) throws StorageException {
    final ValueContainer<Value> container = myCache.get(key);
    if (container != null) {
      return container;
    }
    return readAndCache(key);
    //return _load(key);
  }

  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    ValueContainerImpl<Value> container = myCache.get(key);
    if (container == null) {
      container = readAndCache(key);
    }
    container.addValue(inputId, value);
    //_saveValue(key, inputId, value);
  }

  public void removeValue(final Key key, final int inputId, final Value value) throws StorageException {
    ValueContainerImpl<Value> container = myCache.get(key);
    try {
      if (container == null) {
        container = myMap.get(key);
        if (container != null) {
          myCache.cacheObject(key, container);
        }
      }
      if (container != null) {
        container.removeValue(inputId, value);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    //_removeValue(key, inputId, value);
  }

  @NotNull
  private ValueContainerImpl<Value> readAndCache(final Key key) throws StorageException {
    try {
      ValueContainerImpl<Value> value = myMap.get(key);
      if (value == null) {
        value = new ValueContainerImpl<Value>();
      }
      myCache.cacheObject(key, value);
      return value;
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  /*
  @NotNull
  private ValueContainerImpl<Value> cachedRead(final Key key) throws StorageException {
    synchronized (myCache) {
      final ValueContainerImpl<Value> container = myCache.get(key);
      if (container != null) {
        return container;
      }
    }
    try {
      ValueContainerImpl<Value> valueContainer = myMap.get(key);
      if (valueContainer == null) {
        valueContainer = new ValueContainerImpl<Value>();
      }
      synchronized (myCache) {
        myCache.cacheObject(key, valueContainer);
      }
      return valueContainer;
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }
  */

  public void remove(final Key key) throws StorageException {
    //_remove(key);
    myKeyBeingRemoved = key;
    try {
      do {
        myCache.remove(key);
      }
      while (myCache.isCached(key));
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    finally {
      myKeyBeingRemoved = null;
    }
  }
  
  private static final class ValueContainerExternalizer<T> implements DataExternalizer<ValueContainerImpl<T>> {
    private final DataExternalizer<T> myExternalizer;

    private ValueContainerExternalizer(DataExternalizer<T> externalizer) {
      myExternalizer = externalizer;
    }

    public void save(final DataOutput out, final ValueContainerImpl<T> container) throws IOException {
      container.write(out, myExternalizer);
    }

    public ValueContainerImpl<T> read(final DataInput in) throws IOException {
      return new ValueContainerImpl<T>(in, myExternalizer);
    }
  }

  /*
  private void _saveValue(Key key, int inputId, Value value) throws StorageException {
    synchronized (myPersistenceLock) {
      final ValueContainerImpl<Value> valueContainer = myUnsavedItems.get(key);
      if (valueContainer != null) {
        valueContainer.addValue(inputId, value);
        return;
      }
    }
    final ValueContainerImpl<Value> valueContainer = cachedRead(key);
    valueContainer.addValue(inputId, value);
    scheduleSave(key, valueContainer);
  }

  private void _removeValue(Key key, int inputId, Value value) throws StorageException {
    synchronized (myPersistenceLock) {
      final ValueContainerImpl<Value> valueContainer = myUnsavedItems.get(key);
      if (valueContainer != null) {
        valueContainer.removeValue(inputId, value);
        return;
      }
    }
    final ValueContainerImpl<Value> valueContainer = cachedRead(key);
    valueContainer.removeValue(inputId, value);
    scheduleSave(key, valueContainer);
  }

  private PersistenceTask myCurrentTask = null;
  private void scheduleSave(final Key key, final ValueContainerImpl<Value> valueContainer) {
    synchronized (myPersistenceLock) {
      myUnsavedItems.put(key, valueContainer);
      if (myCurrentTask == null) {
        myCurrentTask = new PersistenceTask();
        ApplicationManager.getApplication().executeOnPooledThread(myCurrentTask);
      }
    }
  }

  private ValueContainerImpl<Value> _load(Key key) throws StorageException {
    synchronized (myPersistenceLock) {
      final ValueContainerImpl<Value> container = myUnsavedItems.get(key);
      if (container != null) {
        return container;
      }
    }
    return cachedRead(key);
  }

  private void _remove(Key key) throws StorageException {
    synchronized (myPersistenceLock) {
      myUnsavedItems.remove(key);
    }
    synchronized (myCache) {
      myCache.remove(key);
    }
    try {
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }
  
  private final class PersistenceTask implements Runnable {
    public void run() {
      boolean shouldStop = false;
      while (true) {
        Key key = null;
        ValueContainerImpl<Value> container = null;
        synchronized (myPersistenceLock) {
          final Iterator<Key> it = myUnsavedItems.keySet().iterator();
          if (it.hasNext()) {
            key = it.next();
            final ValueContainerImpl<Value> _container = myUnsavedItems.get(key);
            final boolean isCached;
            synchronized (myCache) {
              isCached = myCache.isCached(key);
            }
            container = isCached ? _container.clone() : _container; // need clone to avoid concurrent data access exception
            it.remove();
          }
          else {
            if (shouldStop) {
              myCurrentTask = null;
              break;
            }
          }
        }
        
        if (key == null) {
          shouldStop = true;
          try {
            Thread.sleep(5000);
          }
          catch (InterruptedException ignored) {
          }
        }
        else {
          shouldStop = false;
          try {
            if (container.size() > 0) {
              myMap.put(key, container);
            }
            else {
              myMap.remove(key);
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    }
  }
  */
  
}
