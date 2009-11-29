package com.jetbrains.python.toolbox;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
* Iterator that has exactly one item.
* User: dcheryasov
* Date: Nov 6, 2009 9:57:41 AM
*/
public class SingleIterator<T> implements Iterator<T> {

  boolean expired;
  private T content;

  public SingleIterator(T content) {
    this.content = content;
    expired = false;
  }

  public boolean hasNext() {
    return !expired;
  }

  public T next() {
    if (hasNext()) {
      expired = true;
      return content;
    }
    else throw new NoSuchElementException("Single iter expired");
  }

  public void remove() {
    throw new UnsupportedOperationException("Can't remove targets from single iter");
  }
}
