/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CoModifiableList<T> extends AbstractList<T> {
  private ArrayList<T> myAfterIteratingElements = null;
  private final List<T> myElements;

  public CoModifiableList(List<T> elements) {
    myElements = elements;
  }

  public synchronized boolean add(T rangeMarker) {
    if (myAfterIteratingElements != null) myAfterIteratingElements.add(rangeMarker);
    else myElements.add(rangeMarker);
    return true;
  }

  public synchronized T remove(int index) {
    return myElements.remove(index);
  }

  public T get(int index) {
    return myElements.get(index);
  }

  public int size() {
    return myElements.size();
  }

  public void forEach(InnerIterator<T> innerIterator) {
    if (myAfterIteratingElements != null) {
      throw new RuntimeException("Nested iterations aren't supported");
    }
    try {
      myAfterIteratingElements = new ArrayList<T>();
      for (Iterator<T> iterator = myElements.iterator(); iterator.hasNext();) {
        T rangeMarker = iterator.next();
        if (rangeMarker == null) continue;
        innerIterator.process(rangeMarker, iterator);
      }
    } finally {
      synchronized(this) {
        for (Iterator<T> iterator = myAfterIteratingElements.iterator(); iterator.hasNext();) {
          T rangeMarker = iterator.next();
          myElements.add(rangeMarker);
        }
        myAfterIteratingElements = null;
      }
    }
  }

  public interface InnerIterator<T> {
    void process(T rangeMarker, Iterator<T> iterator);
  }
}
