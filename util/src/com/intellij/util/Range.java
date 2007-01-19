package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public class Range<T extends Comparable<T>> {

  private T myFrom;
  private T myTo;


  public Range(@NotNull final T from, @NotNull final T to) {
    myFrom = from;
    myTo = to;
  }

  public boolean isWithin(T object) {
    return object.compareTo(myFrom) >= 0 && object.compareTo(myTo) <= 0;
  }


  public T getFrom() {
    return myFrom;
  }

  public T getTo() {
    return myTo;
  }
}
