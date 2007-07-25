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
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UserDataHolderBase implements UserDataHolderEx, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.UserDataHolderBase");

  private volatile Map<Key, Object> myUserMap = null;

  private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
  private static final Lock r = rwl.readLock();
  private static final Lock w = rwl.writeLock();

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
    r.lock();
    try {
      if (myUserMap != null) {
        final Map copyableMap = (Map)myUserMap.get(COPYABLE_USER_MAP_KEY);
        if (copyableMap == null) {
          return myUserMap.toString();
        }
        else {
          return myUserMap.toString() + copyableMap.toString();
        }
      }
      else {
        return "";
      }
    }
    finally {
      r.unlock();
    }
  }

  public <T> T getUserData(Key<T> key) {
    if (myUserMap == null) return null;

    r.lock();
    try {
      return myUserMap == null ? null : (T)myUserMap.get(key);
    }
    finally {
      r.unlock();
    }
  }

  public <T> void putUserData(Key<T> key, T value) {
    w.lock();
    try {
      if (myUserMap == null) {
        if (value == null) return;
        myUserMap = new THashMap<Key, Object>(2, 0.9f);
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
    finally {
      w.unlock();
    }
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  protected <T> T getCopyableUserDataImpl(Key<T> key) {
    r.lock();
    try {
      Map map = getUserData(COPYABLE_USER_MAP_KEY);
      if (map == null) return null;
      return (T)map.get(key);
    }
    finally {
      r.unlock();
    }
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  protected <T> void putCopyableUserDataImpl(Key<T> key, T value) {
    w.lock();
    try {
      Map<Key, Object> map = getUserData(COPYABLE_USER_MAP_KEY);
      if (map == null) {
        if (value == null) return;
        map = new THashMap<Key, Object>(1, 0.9f);
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
    finally {
      w.unlock();
    }
  }

  @NotNull
  public <T> T putUserDataIfAbsent(@NotNull final Key<T> key, @NotNull final T value) {
    w.lock();
    try {
      if (myUserMap == null) {
        myUserMap = new THashMap<Key, Object>(2, 0.9f);
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
    finally {
      w.unlock();
    }
  }

  protected void copyCopyableDataTo(UserDataHolderBase clone) {
    r.lock();
    Map<Key, Object> copyableMap;
    try {
      copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
      if (copyableMap != null) {
        copyableMap = ((THashMap)copyableMap).clone();
      }
    }
    finally {
      r.unlock();
    }
    clone.putUserData(COPYABLE_USER_MAP_KEY, copyableMap);
  }

  protected void clearUserData() {
    w.lock();
    try {
       myUserMap = null;
    }
    finally{
      w.unlock();
    }
  }
}
