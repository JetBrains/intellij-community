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

import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

public class ClassMapCachingNulls<T> {
  private static final Object[] NULL = ArrayUtil.EMPTY_OBJECT_ARRAY;
  private final Map<Class, T[]> myBackingMap;
  private final Map<Class, T[]> myMap = new ConcurrentHashMap<Class, T[]>();

  public ClassMapCachingNulls(@NotNull Map<Class, T[]> backingMap) {
    myBackingMap = backingMap;
  }

  public T[] get(Class aClass) {
    T[] value = myMap.get(aClass);
    if (value != null) {
      if (value == NULL) {
        return null;
      }
      else {
        assert value.length != 0;
        return value;
      }
    }
    value = myBackingMap.get(aClass);
    List<T> result = null;
    if (value != null) {
      assert value.length != 0;
      result = new ArrayList<T>(Arrays.asList(value));
    }
    for (final Class aClass1 : ReflectionCache.getInterfaces(aClass)) {
      result = addFromUpper(result, aClass1);
    }
    final Class superclass = ReflectionCache.getSuperClass(aClass);
    if (superclass != null) {
      result = addFromUpper(result, superclass);
    }
    if (result == null) {
      myMap.put(aClass, (T[])NULL);
      value = null;
    }
    else {
      Class<T> type = (Class<T>)result.get(0).getClass();
      value = ArrayUtil.toObjectArray(result, type);
      myMap.put(aClass, value);
    }
    return value;
  }

  private List<T> addFromUpper(List<T> value, Class superclass) {
    T[] fromUpper = get(superclass);
    if (fromUpper != null) {
      assert fromUpper.length != 0;
      if (value == null) {
        value = new ArrayList<T>(fromUpper.length);
      }
      for (T t : fromUpper) {
        if (!value.contains(t)) {
          value.add(t);
        }
      }
      assert !value.isEmpty();
    }
    return value;
  }

  public void clearCache() {
    myMap.clear();
  }
}