/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SequenceIterator<T> implements Iterator<T> {
  private Iterator[] myIterators;
  private int myCurrentIndex;

  public SequenceIterator(Iterator[] iterators){
    myIterators = new Iterator[iterators.length];
    for (int i = 0; i < iterators.length; i++){
      Iterator iterator = iterators[i];
      myIterators[i] = iterator;
    }
  }

  public boolean hasNext(){
    if(myCurrentIndex >= myIterators.length){
      return false;
    }
    else if(myIterators[myCurrentIndex] == null){
      myCurrentIndex++;
      return hasNext();
    }
    else if(myIterators[myCurrentIndex].hasNext()){
      return true;
    }
    else{
      myCurrentIndex++;
      return hasNext();
    }
  }

  public T next(){
    if(hasNext()){
      return (T)myIterators[myCurrentIndex].next();
    }
    throw new NoSuchElementException("Iterator has no more elements");
  }

  public void remove(){
    throw new UnsupportedOperationException("Remove not supported");
  }

  public static <T> SequenceIterator<T> create(Iterator<T> first, Iterator<T> second) {
    return new SequenceIterator<T>(new Iterator[]{first, second});
  }

  public static <T> SequenceIterator<T> create(Iterator<T> first, Iterator<T> second, Iterator<T> third) {
    return new SequenceIterator<T>(new Iterator[]{first, second, third});
  }
}

