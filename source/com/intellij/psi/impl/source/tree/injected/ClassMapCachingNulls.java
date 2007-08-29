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
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClassMapCachingNulls<T> {
  private static final List NULL = new ArrayList(0);
  private final Map<Class, List<T>> myBackingMap;
  private final Map<Class, List<T>> myMap = new ConcurrentHashMap<Class, List<T>>();

  public ClassMapCachingNulls(@NotNull Map<Class, List<T>> backingMap) {
    myBackingMap = backingMap;
  }

  public List<T> get(Class aClass) {
    List<T> value = myMap.get(aClass);
    if (value == null) {
      value = myBackingMap.get(aClass);
      if (value != null) {
        myMap.put(aClass, value);
      }
    }
    if (value != null) {
      return value == NULL ? null : value;
    }

    for (final Class aClass1 : ReflectionCache.getInterfaces(aClass)) {
      value = addFromUpper(value, aClass1);
    }
    final Class superclass = ReflectionCache.getSuperClass(aClass);
    if (superclass != null) {
      value = addFromUpper(value, superclass);
    }
    if (value == NULL) {
      value = null;
    }
    myMap.put(aClass, value == null ? (List<T>)NULL : value);
    return value;
  }

  private List<T> addFromUpper(List<T> value, Class superclass) {
    List<T> fromUpper = get(superclass);
    if (fromUpper != null) {
      if (value == null) {
        value = new ArrayList<T>(fromUpper);
      }
      else {
        for (T t : fromUpper) {
          if (!value.contains(t)) {
            value.add(t);
          }
        }
      }
    }
    return value;
  }

  public void clearCache() {
    myMap.clear();
  }
}