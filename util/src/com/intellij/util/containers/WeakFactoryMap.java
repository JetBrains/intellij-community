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
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.lang.ref.WeakReference;

/**
 * @author peter
 */
public abstract class WeakFactoryMap<T,V> {
  private final Map<T, WeakReference<V>> myMap = new WeakHashMap<T, WeakReference<V>>();

  @NotNull
  protected abstract V create(T key);

  @NotNull
  public final V get(T key) {
    WeakReference<V> reference = myMap.get(key);
    if (reference != null) {
      final V v = reference.get();
      if (v != null) {
        return v;
      }
    }

    final V v1 = create(key);
    myMap.put(key, reference = new WeakReference<V>(v1));
    return v1;
  }

  public final boolean containsKey(T key) {
    return myMap.containsKey(key);
  }

}
