package com.intellij.openapi.util;


import com.intellij.util.containers.LockPoolSynchronizedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class UserDataHolderBase implements UserDataHolderEx, Cloneable {
  private static final Object MAP_LOCK = new Object();
  private static final Key<Map<Key, Object>> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  private volatile LockPoolSynchronizedMap<Key, Object> myUserMap = null;

  protected Object clone() {
    try {
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      clone.myUserMap = null;
      copyCopyableDataTo(clone);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);

    }
  }

  public String getUserDataString() {
    final Map<Key, Object> userMap = myUserMap;
    if (userMap == null) {
      return "";
    }
    final Map copyableMap = (Map)userMap.get(COPYABLE_USER_MAP_KEY);
    if (copyableMap == null) {
      return userMap.toString();
    }
    else {
      return userMap.toString() + copyableMap.toString();
    }
  }

  public void copyUserDataTo(UserDataHolderBase other) {
    if (myUserMap == null) {
      other.myUserMap = null;
    }
    else {
      LockPoolSynchronizedMap<Key, Object> fresh = createMap();
      fresh.putAll(myUserMap);
      other.myUserMap = fresh;
    }
  }

  public <T> T getUserData(Key<T> key) {
    final Map<Key, Object> map = myUserMap;
    return map == null ? null : (T)map.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    LockPoolSynchronizedMap<Key, Object> map = getOrCreateMap();

    if (value == null) {
      map.remove(key);
    }
    else {
      map.put(key, value);
    }
  }

  private static LockPoolSynchronizedMap<Key, Object> createMap() {
    return new LockPoolSynchronizedMap<Key, Object>(2, 0.9f);
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  protected <T> T getCopyableUserDataImpl(Key<T> key) {
    Map map = getUserData(COPYABLE_USER_MAP_KEY);
    return map == null ? null : (T)map.get(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  protected <T> void putCopyableUserDataImpl(Key<T> key, T value) {
    LockPoolSynchronizedMap<Key, Object> map = getOrCreateMap();
    map.getWriteLock().lock();
    try {
      Map<Key, Object> copyMap = getUserData(COPYABLE_USER_MAP_KEY);
      if (copyMap == null) {
        if (value == null) return;
        copyMap = new LockPoolSynchronizedMap<Key, Object>(1, 0.9f);
        putUserData(COPYABLE_USER_MAP_KEY, copyMap);
      }

      if (value != null) {
        copyMap.put(key, value);
      }
      else {
        copyMap.remove(key);
        if (copyMap.isEmpty()) {
          map.remove(COPYABLE_USER_MAP_KEY);
        }
      }
    }
    finally {
      map.getWriteLock().unlock();
    }
  }


  private LockPoolSynchronizedMap<Key, Object> getOrCreateMap() {
    if (myUserMap == null) {
      synchronized (MAP_LOCK) {
        if (myUserMap == null) {
          myUserMap = createMap();
        }
      }
    }

    return myUserMap;
  }

  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    return getOrCreateMap().replace(key, oldValue, newValue);
  }

  @NotNull
  public <T> T putUserDataIfAbsent(@NotNull final Key<T> key, @NotNull final T value) {
    return (T)getOrCreateMap().putIfAbsent(key, value);
  }

  public void copyCopyableDataTo(UserDataHolderBase clone) {
    Map<Key, Object> copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    if (copyableMap != null) {
      copyableMap = ((LockPoolSynchronizedMap)copyableMap).clone();
    }
    clone.putUserData(COPYABLE_USER_MAP_KEY, copyableMap);
  }

  protected void clearUserData() {
    synchronized (MAP_LOCK) {
      myUserMap = null;
    }
  }
}
