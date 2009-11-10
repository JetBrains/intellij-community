package com.jetbrains.python.toolbox;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator over an array.
 * @param <T> array element type
 */
public class ArrayIterator<T> implements Iterator<T> {
  // TODO: implement ListIterator

  protected int my_index;
  protected T[] content;

  public ArrayIterator(T[] content) {
    this.content = content;
    my_index = 0;
  }

  public boolean hasNext() {
    return ((content != null) && (my_index < content.length));
  }

  public T next() {
    if (hasNext()) {
      T ret = content[my_index];
      my_index +=  1;
      return ret;
    }
    else throw new NoSuchElementException(content == null ? "Null content" : "Only got " + content.length + "items");
  }

  public void remove() {
    throw new UnsupportedOperationException("Can't remove targets from iter");
  }
}
