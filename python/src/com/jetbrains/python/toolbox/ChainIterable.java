/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.toolbox;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;

/**
 * Iterable that splices other iterables and iterates over them sequentially.
 * User: dcheryasov
 * Date: Nov 20, 2009 8:01:23 AM
 */
public class ChainIterable<T> extends ChainedListBase<Iterable<T>> implements Iterable<T> {

  public ChainIterable(@Nullable Iterable<T> initial) {
    super(initial);
  }

  public ChainIterable() {
    super(null);
  }
  
  public ChainIterable(@NotNull T initial) {
    super(Collections.singleton(initial));
  }


  public ChainIterable<T> add(@NotNull Iterable<T> another) {
    return (ChainIterable<T>)super.add(another);
  }

  /**
   * Apply wrapper to another and add the result. Convenience to avoid cluttering code with apply() calls.
   * @param wrapper
   * @param another
   * @return
   */
  public ChainIterable<T> addWith(FP.Lambda1<Iterable<T>, Iterable<T>> wrapper, Iterable<T> another) {
    return (ChainIterable<T>)super.add(wrapper.apply(another));
  }

  /**
   * Convenience: add an item wrapping it into a SingleIterable behind the scenes.
   */
  public ChainIterable<T> addItem(@NotNull T item) {
    return (ChainIterable<T>)super.add(Collections.<T>singleton(item));
  }

  /**
   * Convenience, works without ever touching an iterator.
   * @return true if the chain contains at least one iterable (but all iterables in the chain may happen to be empty).
   */
  public boolean isEmpty() {
    return (myPayload == null);
  }

  public Iterator<T> iterator() {


    class IterMixedIn extends ChainIterationMixin<T, Iterable<T>> {
      IterMixedIn(ChainedListBase<Iterable<T>> link) {
        super(link);
      }

      @Override
      public Iterator<T> toIterator(Iterable<T> first) {
        return first.iterator();
      }
    }
    final IterMixedIn mixin = new IterMixedIn(this);


    class Iter extends ChainedListBase<Iterable<T>> implements Iterator<T> {

      Iter(ChainedListBase<Iterable<T>> piggybacked) {
        super(piggybacked.myPayload);
        myNext = piggybacked.myNext;
      }

      public boolean hasNext() {
        return mixin.hasNext();
      }

      public void remove() {
        throw new UnsupportedOperationException(); // we don't remove things
      }

      public T next() {
        //noinspection RedundantCast
        return (T)mixin.next();
      }

    }

    return new Iter(this);

  }

  @Override
  public String toString() {
    return FP.fold(new FP.StringCollector<T>(), this, new StringBuilder()).toString();
  }
}
