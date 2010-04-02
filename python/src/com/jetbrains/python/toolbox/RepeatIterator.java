package com.jetbrains.python.toolbox;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An iterator that endlessly repeats the same contents.
 * User: dcheryasov
 * Date: Nov 8, 2009 5:55:57 AM
 */
public class RepeatIterator<T> implements Iterator<T> {
  private final List<T> master;
  private Iterator<T> source;

  /**
   * Create an iterator that repeats the contents of given list.
   * @param master the list to repeat
   */
  public RepeatIterator(@NotNull List<T> master) {
    this.master = master;
    this.source = master.iterator();
  }

  /**
   * Create an iterator that repeats given value.
   * @param single the value to repeat
   */
  public RepeatIterator(@NotNull T single) {
    master = new ArrayList<T>(1);
    master.add(single);
    source = master.iterator();
  }

  public boolean hasNext() {
    return master.size() > 0;
  }

  public void remove() {
    throw new UnsupportedOperationException("This is a read-only endless iterator");
  }

  public T next() {
    if (! hasNext()) throw new NoSuchElementException();
    if (!source.hasNext()) source = master.iterator();
    return source.next();
  }
}
