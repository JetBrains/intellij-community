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

package com.intellij.util;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class FilteredQuery<T> implements Query<T> {
  private Query<T> myOriginal;
  private final Condition<T> myFilter;

  public FilteredQuery(final Query<T> original, Condition<T> filter) {
    myOriginal = original;
    myFilter = filter;
  }

  public T findFirst() {
    final CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<T>();
    forEach(processor);
    return processor.getFoundValue();
  }

  public boolean forEach(final Processor<T> consumer) {
    myOriginal.forEach(new Processor<T>() {
      public boolean process(final T t) {
        if (!myFilter.value(t)) return true;
        if (!consumer.process(t)) return false;

        return true;
      }
    });

    return true;
  }

  @NotNull
  public Collection<T> findAll() {
    final List<T> result = new ArrayList<T>();
    forEach(new Processor<T>() {
      public boolean process(final T t) {
        result.add(t);
        return true;
      }
    });

    return result;
  }

  public T[] toArray(final T[] a) {
    return findAll().toArray(a);
  }

  public Iterator<T> iterator() {
    return findAll().iterator();
  }
}
