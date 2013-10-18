package com.jetbrains.python.toolbox;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Iterable that endlessly repeat the same sequence.
 * User: dcheryasov
 * Date: Nov 8, 2009 6:32:08 AM
 */
public class RepeatIterable<T> implements Iterable<T> {
  private final List<T> master;

  /**
   * Create an iterator that repeats the contents of given list.
   * @param master the list to repeat
   */
  public RepeatIterable(@NotNull List<T> master) {
    this.master = master;
  }

  /**
   * Create an iterator that repeats given value.
   * @param single the value to repeat
   */
  public RepeatIterable(@NotNull T single) {
    master = new ArrayList<T>(1);
    master.add(single);
  }

  public Iterator<T> iterator() {
    return new RepeatIterator<T>(master);
  }
}
