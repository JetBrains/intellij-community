/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentClassMap;

import java.util.Iterator;
import java.util.Map;

public class ClassMapCachingNulls<T> extends ConcurrentClassMap<T> {
  private static final Object NULL = new Object();
  public T get(Class aClass) {
    T value = myMap.get(aClass);
    if (value != null) {
      return value == NULL ? null : value;
    }
    for (final Class aClass1 : ReflectionCache.getInterfaces(aClass)) {
      value = get(aClass1);
      if (value != null) {
        break;
      }
    }
    if (value == null) {
      final Class superclass = ReflectionCache.getSuperClass(aClass);
      if (superclass != null) {
        value = get(superclass);
      }
    }
    if (value == NULL) {
      value = null;
    }
    myMap.put(aClass, value == null ? (T)NULL : value);
    return value;
  }

  public void clearCachedNulls() {
    Iterator<Map.Entry<Class,T>> iterator = myMap.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Class, T> entry = iterator.next();
      if (entry.getValue() == NULL) {
        iterator.remove();
      }
    }
  }
}