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
