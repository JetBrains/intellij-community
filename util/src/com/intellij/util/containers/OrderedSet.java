/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;

public class OrderedSet<T> extends AbstractSet<T> {
  private GenericHashSet<T> myHashSet;
  private ArrayList<T> myElements;

  public OrderedSet(TObjectHashingStrategy<T> hashingStrategy) {
    myHashSet = new GenericHashSet<T>(hashingStrategy);
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
      newSet.myHashSet = (GenericHashSet)myHashSet.clone();
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

}