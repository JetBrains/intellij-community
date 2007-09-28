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
package com.intellij.util.containers;

import com.intellij.openapi.diagnostic.Logger;

import java.util.AbstractList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class WeakList<T> extends AbstractList<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.containers.WeakList");
  private final WeakReferenceArray<T> myArray;

  public WeakList() {
    this(new WeakReferenceArray<T>());
  }

  // For testing only
  WeakList(WeakReferenceArray<T> array) {
    myArray = array;
  }

  public T get(int index) {
    synchronized (myArray) {
      return myArray.get(index);
    }
  }

  public boolean add(T element) {
    synchronized (myArray) {
      tryReduceCapacity(-1);
      myArray.add(element);
    }
    return true;
  }

  public void add(int index, T element) {
    synchronized (myArray) {
      tryReduceCapacity(-1);
      myArray.add(index, element);
    }
  }

  public T remove(int index) {
    synchronized (myArray) {
      tryReduceCapacity(-1);
      return myArray.remove(index);
    }
  }

  public Iterator<T> iterator() {
    return new MyIterator();
  }

  public int size() {
    synchronized (myArray) {
      return myArray.size();
    }
  }

  private int tryReduceCapacity(int trackIndex) {
    modCount++;
    if (canReduceCapacity()) {
      return myArray.reduceCapacity(trackIndex);
    }
    else {
      return propablyCompress(trackIndex);
    }
  }

  private int myCompressCountdown = 10;
  private int propablyCompress(int trackIndex) {
    myCompressCountdown--;
    if (myCompressCountdown > 0) return trackIndex;
    int newIndex = myArray.compress(trackIndex);
    myCompressCountdown = myArray.size() + 10;
    return newIndex;
  }

  private boolean canReduceCapacity() {
    return WeakReferenceArray.MINIMUM_CAPACITY*2 < myArray.getCapacity() &&
           myArray.getCapacity() > myArray.getAliveCount()*3;
  }

  private class MyIterator implements Iterator<T> {
    private int myNextIndex = -1;
    private int myCurrentIndex = -1;
    private T myNextElement = null;
    private int myModCount = modCount;

    public MyIterator() {
      findNext();
    }

    private void findNext() {
      synchronized (myArray) {
        myNextElement = null;
        while (myNextElement == null) {
          myNextIndex = myArray.nextValid(myNextIndex);
          if (myNextIndex >= myArray.size()) {
            myNextIndex = -1;
            myNextElement = null;
            return;
          }
          myNextElement = myArray.get(myNextIndex);
        }
      }
    }

    public boolean hasNext() {
      return myNextElement != null;
    }

    public T next() {
      if (modCount != myModCount) throw new ConcurrentModificationException();
      if (myNextElement == null) throw new NoSuchElementException();
      T element = myNextElement;
      myCurrentIndex = myNextIndex;
      findNext();
      return element;
    }

    public void remove() {
      synchronized (myArray) {
        if (myCurrentIndex == -1) throw new IllegalStateException();
        myArray.remove(myCurrentIndex);
        final int removedIndex = myCurrentIndex;
        int newIndex = tryReduceCapacity(myNextIndex);
        myCurrentIndex = -1;
        myModCount = modCount;
        if (!hasNext()) return;
        if (newIndex < 0) {
          LOG.error(" was: " + myNextIndex +
                    " got: " + newIndex +
                    " size: " + myArray.size() +
                    " current: " + removedIndex);
        }
        myNextIndex = newIndex;
        LOG.assertTrue(myArray.get(myNextIndex) == myNextElement);
      }
    }
  }
}
