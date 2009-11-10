package com.jetbrains.python.toolbox;

import java.util.Iterator;

/**
 * Convenience iterator wrapper; makes items cast to given type (presumably PyExpression).
 * @param <T> class to cast to.
 */
public class ArrayIterable<T> implements Iterable<T> {
  protected T[] mySource;
  public ArrayIterable(T[] source){
    mySource = source;
  }

  public Iterator<T> iterator() {
    return new ArrayIterator<T>(mySource);
  }
}
