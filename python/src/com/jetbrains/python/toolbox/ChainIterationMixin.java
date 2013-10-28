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

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Common logic of chain iterators.
 * User: dcheryasov
 * Date: Nov 20, 2009 9:10:39 AM
 */
/* explicitly not public */
abstract class ChainIterationMixin<T, TPayload> {
  protected ChainedListBase<TPayload> myLink; // link of the chain we're currently at

  protected Iterator<T> myCurrent;

  public ChainIterationMixin(ChainedListBase<TPayload> link) {
    myLink = link;
  }

  abstract public Iterator<T> toIterator(TPayload first);

  // returns either null or a non-exhausted iterator.
  @Nullable
  public Iterator<T> getCurrent() {
    while ((myCurrent == null || !myCurrent.hasNext()) && myLink.hasPayload()) { // fix myCurrent
      if (myCurrent == null) {
        myCurrent = toIterator(myLink.myPayload);
        assert myCurrent != null;
      }
      else {
        myLink.moveOn();
        myCurrent = null;
      }
    }
    return myCurrent;
  }

  public boolean hasNext() {
    Iterator<T> current = getCurrent();
    return (current != null);
  }

  public T next() {
    Iterator<T> current = getCurrent();
    if (current != null) return current.next();
    else throw new NoSuchElementException();
  }

}
