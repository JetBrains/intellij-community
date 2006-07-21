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

import java.util.Map;

public class UserDataHolderBase implements UserDataHolder, Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.UserDataHolderBase");

  private Map<Key,Object> myUserMap = null;

  private static final Object USER_MAP_LOCK = new Object();
  protected static final Key<Map<Key,Object>> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  protected Object clone(){
    try{
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      Map<Key,Object> copyableMap = clone.getUserData(COPYABLE_USER_MAP_KEY);
      clone.myUserMap = null;
      if (copyableMap != null){
        final Map<Key,Object> mapclone = ((THashMap<Key, Object>)copyableMap).clone();
        clone.putUserData(COPYABLE_USER_MAP_KEY, mapclone);
      }
      return clone;
    }
    catch(CloneNotSupportedException e){
      LOG.error(e);
      return null;
    }
  }

  public String getUserDataString() {
    synchronized(USER_MAP_LOCK) {
      if (myUserMap != null) {
        final Map copyableMap = (Map)myUserMap.get(COPYABLE_USER_MAP_KEY);
        if (copyableMap == null) {
          return myUserMap.toString();
        } else {
          return myUserMap.toString() + copyableMap.toString();
        }
      } else {
        return "";
      }
    }
  }



  public <T> T getUserData(Key<T> key) {
    synchronized(USER_MAP_LOCK){
      if (myUserMap == null) return null;
      return (T)myUserMap.get(key);
    }
  }

  public <T> void putUserData(Key<T> key, T value) {
    synchronized(USER_MAP_LOCK){
      if (myUserMap == null){
        if (value == null) return;
        myUserMap = new THashMap<Key, Object>(2, 0.9f);
      }
      if (value != null){
        myUserMap.put(key, value);
      }
      else{
        myUserMap.remove(key);
        if (myUserMap.size() == 0){
          myUserMap = null;
        }
      }
    }
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  protected <T> T getCopyableUserDataImpl(Key<T> key) {
    synchronized(USER_MAP_LOCK){
      Map map = getUserData(COPYABLE_USER_MAP_KEY);
      if (map == null) return null;
      return (T)map.get(key);
    }
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  protected <T> void putCopyableUserDataImpl(Key<T> key, T value) {
    synchronized(USER_MAP_LOCK){
      Map<Key,Object> map = getUserData(COPYABLE_USER_MAP_KEY);
      if (map == null){
        if (value == null) return;
        map = new THashMap<Key, Object>(1, 0.9f);
        putUserData(COPYABLE_USER_MAP_KEY, map);
      }
      if (value != null){
        map.put(key, value);
      }
      else{
        map.remove(key);
        if (map.size() == 0){
          putUserData(COPYABLE_USER_MAP_KEY, null);
        }
      }
    }
  }
}
