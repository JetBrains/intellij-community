/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;

public class UserDataHolderBase implements UserDataHolder, Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.UserDataHolderBase");

  private THashMap myUserMap = null;

  private static final Object USER_MAP_LOCK = new Object();
  protected static final Key<HashMap> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  protected Object clone(){
    try{
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      HashMap copyableMap = (HashMap)clone.getUserData(COPYABLE_USER_MAP_KEY);
      clone.myUserMap = null;
      if (copyableMap != null){
        clone.putUserData(COPYABLE_USER_MAP_KEY, (HashMap)copyableMap.clone());
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
        final HashMap copyableMap = (HashMap) myUserMap.get(COPYABLE_USER_MAP_KEY);
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
        myUserMap = new THashMap(4);
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
      HashMap map = (HashMap)getUserData(COPYABLE_USER_MAP_KEY);
      if (map == null) return null;
      return (T)map.get(key);
    }
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  protected <T> void putCopyableUserDataImpl(Key<T> key, T value) {
    synchronized(USER_MAP_LOCK){
      HashMap map = (HashMap)getUserData(COPYABLE_USER_MAP_KEY);
      if (map == null){
        if (value == null) return;
        map = new HashMap(4);
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
