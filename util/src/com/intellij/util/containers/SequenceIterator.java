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

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SequenceIterator<T> implements Iterator<T> {
  private Iterator[] myIterators;
  private int myCurrentIndex;

  public SequenceIterator(Iterator... iterators){
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

