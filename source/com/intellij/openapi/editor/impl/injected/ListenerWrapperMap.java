package com.intellij.openapi.editor.impl.injected;

import gnu.trove.THashMap;

import java.util.EventListener;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

/**
 * @author cdr
*/
class ListenerWrapperMap<T extends EventListener> {
  Map<T,T> myListener2WrapperMap = new THashMap<T, T>();

  void registerWrapper(T listener, T wrapper) {
    myListener2WrapperMap.put(listener, wrapper);
  }
  T removeWrapper(T listener) {
    return myListener2WrapperMap.remove(listener);
  }

  public Collection<T> wrappers() {
    return myListener2WrapperMap.values();
  }

  public String toString() {
    return new HashMap<T,T>(myListener2WrapperMap).toString();
  }

  public void clear() {
    myListener2WrapperMap.clear();
  }
}
