/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.LockPoolSynchronizedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class UserDataHolderBase implements UserDataHolderEx, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.UserDataHolderBase");

  private volatile Map<Key, Object> myUserMap = null;
  private static final Object WRITE_LOCK = new Object();

  protected static final Key<Map<Key, Object>> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  protected Object clone() {
    try {
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      clone.myUserMap = null;
      copyCopyableDataTo(clone);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public String getUserDataString() {
    final Map<Key, Object> userMap = myUserMap;
    if (userMap != null) {
      final Map copyableMap = (Map)userMap.get(COPYABLE_USER_MAP_KEY);
      if (copyableMap == null) {
        return userMap.toString();
      }
      else {
        return userMap.toString() + copyableMap.toString();
      }
    }
    else {
      return "";
    }
  }

  public <T> T getUserData(Key<T> key) {
    final Map<Key, Object> map = myUserMap;
    return map != null ? (T)myUserMap.get(key) : null;
  }

  public <T> void putUserData(Key<T> key, T value) {
    synchronized (WRITE_LOCK) {
      if (myUserMap == null) {
        if (value == null) return;
        myUserMap = createMap();
      }
      if (value != null) {
        myUserMap.put(key, value);
      }
      else {
        myUserMap.remove(key);
        if (myUserMap.isEmpty()) {
          myUserMap = null;
        }
      }
    }
  }

  private static <T> LockPoolSynchronizedMap<Key, Object> createMap() {
    return new LockPoolSynchronizedMap<Key, Object>(2, 0.9f);
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  protected <T> T getCopyableUserDataImpl(Key<T> key) {
    Map map = getUserData(COPYABLE_USER_MAP_KEY);
    return map != null ? (T)map.get(key) : null;
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  protected <T> void putCopyableUserDataImpl(Key<T> key, T value) {
    synchronized (WRITE_LOCK) {
      Map<Key, Object> map = getUserData(COPYABLE_USER_MAP_KEY);
      if (map == null) {
        if (value == null) return;
        map = new LockPoolSynchronizedMap<Key, Object>(1, 0.9f);
        putUserData(COPYABLE_USER_MAP_KEY, map);
      }

      if (value != null) {
        map.put(key, value);
      }
      else {
        map.remove(key);
        if (map.isEmpty()) {
          putUserData(COPYABLE_USER_MAP_KEY, null);
        }
      }
    }
  }

  @NotNull
  public <T> T putUserDataIfAbsent(@NotNull final Key<T> key, @NotNull final T value) {
    synchronized (WRITE_LOCK) {
      if (myUserMap == null) {
        myUserMap = createMap();
        myUserMap.put(key, value);
        return value;
      }
      T prev = (T)myUserMap.get(key);
      if (prev == null) {
        myUserMap.put(key, value);
        return value;
      }
      else {
        return prev;
      }
    }
  }

  public <T> boolean replace(@NotNull Key<T> key, @NotNull T oldValue, @Nullable T newValue) {
    synchronized (WRITE_LOCK) {
      if (myUserMap == null) {
        if (newValue != null) {
          myUserMap = createMap();
          myUserMap.put(key, newValue);
        }
        return true;
      }
      T prev = (T)myUserMap.get(key);
      if (prev == null || prev.equals(oldValue)) {
        if (newValue != null) {
          myUserMap.put(key, newValue);
        }
        else {
          myUserMap.remove(key);
          if (myUserMap.isEmpty()) {
            myUserMap = null;
          }
        }
        return true;
      }
      return false;
    }
  }

  protected void copyCopyableDataTo(UserDataHolderBase clone) {
    Map<Key, Object> copyableMap;
    copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    if (copyableMap != null) {
      copyableMap = ((LockPoolSynchronizedMap)copyableMap).clone();
    }
    clone.putUserData(COPYABLE_USER_MAP_KEY, copyableMap);
  }

  protected void clearUserData() {
    synchronized (WRITE_LOCK) {
      myUserMap = null;
    }
  }
}
