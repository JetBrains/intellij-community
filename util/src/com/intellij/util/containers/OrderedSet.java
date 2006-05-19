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

import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;

import java.util.*;

public class OrderedSet<T> extends AbstractSet<T> implements List<T> {
  private THashSet<T> myHashSet;
  private ArrayList<T> myElements;

  public OrderedSet(TObjectHashingStrategy<T> hashingStrategy) {
    myHashSet = new THashSet<T>(hashingStrategy);
    myElements = new ArrayList<T>();
  }

  public Iterator<T> iterator() {
    return new MyIterator();
  }

  public int size() {
    return myElements.size();
  }

  public boolean contains(Object o) {
    return myHashSet.contains(o);
  }

  public boolean add(T o) {
    if (myHashSet.add(o)){
      myElements.add(o);
      return true;
    }
    else{
      return false;
    }
  }

  public boolean remove(Object o) {
    if (myHashSet.remove(o)){
      myElements.remove(o);
      return true;
    }
    else{
      return false;
    }
  }

  public void clear() {
    myHashSet.clear();
    myElements.clear();
  }

  public Object[] toArray() {
    return myElements.toArray();
  }

  public <T> T[] toArray(T[] a) {
    return myElements.toArray(a);
  }

  public Object clone() {
    try{
      OrderedSet newSet = (OrderedSet)super.clone();
      newSet.myHashSet = (THashSet)myHashSet.clone();
      newSet.myElements = (ArrayList)myElements.clone();
      return newSet;
    }
    catch(CloneNotSupportedException e){
      throw new InternalError();
    }
  }

  private class MyIterator implements Iterator<T> {
    private Iterator<T> myIterator = myElements.iterator();
    private T myLastObject;

    public boolean hasNext() {
      return myIterator.hasNext();
    }

    public T next() {
      return myLastObject = myIterator.next();
    }

    public void remove() {
      myIterator.remove();
      myHashSet.remove(myLastObject);
    }
  }


  public boolean addAll(final int index, final Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  public T get(final int index) {
    return myElements.get(index);
  }

  public T set(final int index, final T element) {
    throw new UnsupportedOperationException();
  }

  public void add(final int index, final T element) {
    throw new UnsupportedOperationException();
  }

  public T remove(final int index) {
    throw new UnsupportedOperationException();
  }

  public int indexOf(final Object o) {
    return myElements.indexOf(o);
  }

  public int lastIndexOf(final Object o) {
    return myElements.lastIndexOf(o);
  }

  public ListIterator<T> listIterator() {
    return myElements.listIterator();
  }

  public ListIterator<T> listIterator(final int index) {
    return myElements.listIterator(index);
  }

  public List<T> subList(final int fromIndex, final int toIndex) {
    return myElements.subList(fromIndex, toIndex);
  }
}