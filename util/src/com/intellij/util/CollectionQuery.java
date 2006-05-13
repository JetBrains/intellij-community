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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author max
 */
public class CollectionQuery<T> implements Query<T> {
  private Collection<T> myCollection;
  private Condition<T> myFilter;


  public CollectionQuery(@NotNull final Collection<T> collection) {
    myCollection = collection;
  }

  public Condition<T> getFilter() {
    return myFilter;
  }

  public void setFilter(final Condition<T> filter) {
    myFilter = filter;
  }

  @NotNull
  public Collection<T> findAll() {
    if (myFilter == null) return myCollection;
    return ContainerUtil.findAll(myCollection, myFilter);
  }

  public T findFirst() {
    final Iterator<T> i = iterator();
    return i.hasNext() ? i.next() : null;
  }

  public boolean forEach(final Processor<T> consumer) {
    for (T t : myCollection) {
      if (myFilter == null || myFilter.value(t)) {
        if (!consumer.process(t)) return false;
      }
    }
    return true;
  }

  public T[] toArray(final T[] a) {
    return findAll().toArray(a);
  }

  public Iterator<T> iterator() {
    if (myFilter == null) return myCollection.iterator();
    return new FilteringIterator<T, T>(myCollection.iterator(), myFilter);
  }
}
