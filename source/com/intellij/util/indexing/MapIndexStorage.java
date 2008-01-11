package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ObjectCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 20, 2007
*/
final class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapIndexStorage");
  private final PersistentHashMap<Key, ValueContainerImpl<Value>> myMap;
  private final ObjectCache<Key, ValueContainerImpl<Value>> myCache;
  private Key myKeyBeingRemoved = null;
  
  private Alarm myCacheFlushingAlarm  = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Runnable myFlushCachesRequest = new Runnable() {
    public void run() {
      flush();
    }
  };

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
    myCache.removeAll();
    try {
      myMap.flush();
    }
    catch (IOException e) {
      LOG.error(e);
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
  }

  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    ((ValueContainerImpl<Value>)read(key)).addValue(inputId, value);
  }

  public void removeValue(final Key key, final int inputId, final Value value) throws StorageException {
    ValueContainerImpl<Value> container = myCache.get(key);
    try {
      if (container == null) {
        container = myMap.get(key);
        if (container != null) {
          cacheObject(key, container);
        }
      }
      if (container != null) {
        container.removeValue(inputId, value);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private void cacheObject(final Key key, final ValueContainerImpl<Value> value) {
    myCache.cacheObject(key, value);
    myCacheFlushingAlarm.cancelAllRequests();
    myCacheFlushingAlarm.addRequest(myFlushCachesRequest, 15000 /* 15 sec */);
  }

  @NotNull
  private ValueContainerImpl<Value> readAndCache(final Key key) throws StorageException {
    try {
      ValueContainerImpl<Value> value = myMap.get(key);
      if (value == null) {
        value = new ValueContainerImpl<Value>();
      }
      cacheObject(key, value);
      return value;
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public void remove(final Key key) throws StorageException {
    myKeyBeingRemoved = key;
    try {
      myCache.remove(key);
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
}
