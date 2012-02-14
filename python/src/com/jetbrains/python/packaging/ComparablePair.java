package com.jetbrains.python.packaging;

import com.intellij.openapi.util.Pair;

/**
 * User: catherine
 */
public class ComparablePair extends Pair<String, String> implements Comparable {

  public ComparablePair(String first, String second) {
    super(first, second);
  }

  @Override
  public int compareTo(Object o) {
    if (first instanceof String && o instanceof ComparablePair)
      return first.compareTo(((ComparablePair)o).getFirst());
    return 0;
  }
}