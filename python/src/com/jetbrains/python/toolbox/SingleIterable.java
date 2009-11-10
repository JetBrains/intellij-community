package com.jetbrains.python.toolbox;

import java.util.Iterator;

/**
 * Iterable that can only have one element.
 * @param <T> element type
 */
public class SingleIterable<T> implements Iterable<T> {

  T content;
  public SingleIterable(T content) {
    this.content = content;
  }

  public Iterator<T> iterator() {
    return new SingleIterator<T>(content);
  }

}
