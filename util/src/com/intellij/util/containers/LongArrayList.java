/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

public class LongArrayList implements Cloneable {
  private long[] myData;
  private int mySize;

  public LongArrayList(int initialCapacity) {
    myData = new long[initialCapacity];
  }

  public LongArrayList() {
    this(10);
  }

  public void trimToSize() {
    int oldCapacity = myData.length;
    if (mySize < oldCapacity){
      long[] oldData = myData;
      myData = new long[mySize];
      System.arraycopy(oldData, 0, myData, 0, mySize);
    }
  }

  public void ensureCapacity(int minCapacity) {
    int oldCapacity = myData.length;
    if (minCapacity > oldCapacity){
      long[] oldData = myData;
      int newCapacity = (oldCapacity * 3) / 2 + 1;
      if (newCapacity < minCapacity){
        newCapacity = minCapacity;
      }
      myData = new long[newCapacity];
      System.arraycopy(oldData, 0, myData, 0, mySize);
    }
  }

  public int size() {
    return mySize;
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  public boolean contains(long elem) {
    return indexOf(elem) >= 0;
  }

  public int indexOf(long elem) {
    for(int i = 0; i < mySize; i++){
      if (elem == myData[i]) return i;
    }
    return -1;
  }

  public int lastIndexOf(long elem) {
    for(int i = mySize - 1; i >= 0; i--){
      if (elem == myData[i]) return i;
    }
    return -1;
  }

  public Object clone() {
    try{
      LongArrayList v = (LongArrayList)super.clone();
      v.myData = new long[mySize];
      System.arraycopy(myData, 0, v.myData, 0, mySize);
      return v;
    }
    catch(CloneNotSupportedException e){
      // this shouldn't happen, since we are Cloneable
      throw new InternalError();
    }
  }

  public long[] toArray() {
    long[] result = new long[mySize];
    System.arraycopy(myData, 0, result, 0, mySize);
    return result;
  }

  public long[] toArray(long[] a) {
    if (a.length < mySize){
      a = new long[mySize];
    }

    System.arraycopy(myData, 0, a, 0, mySize);

    return a;
  }

  public long get(int index) {
    checkRange(index);
    return myData[index];
  }

  public long set(int index, long element) {
    checkRange(index);

    long oldValue = myData[index];
    myData[index] = element;
    return oldValue;
  }

  public void add(long o) {
    ensureCapacity(mySize + 1);
    myData[mySize++] = o;
  }

  public void add(int index, long element) {
    if (index > mySize || index < 0){
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }

    ensureCapacity(mySize + 1);
    System.arraycopy(myData, index, myData, index + 1, mySize - index);
    myData[index] = element;
    mySize++;
  }

  public long remove(int index) {
    checkRange(index);

    long oldValue = myData[index];

    int numMoved = mySize - index - 1;
    if (numMoved > 0){
      System.arraycopy(myData, index + 1, myData, index,numMoved);
    }
    mySize--;

    return oldValue;
  }

  public void clear() {
    mySize = 0;
  }

  protected void removeRange(int fromIndex, int toIndex) {
    int numMoved = mySize - toIndex;
    System.arraycopy(myData, toIndex, myData, fromIndex, numMoved);
    mySize -= (toIndex - fromIndex);
  }

  private void checkRange(int index) {
    if (index >= mySize || index < 0){
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }
  }
}
