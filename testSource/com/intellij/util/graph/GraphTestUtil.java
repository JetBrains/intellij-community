package com.intellij.util.graph;

import junit.framework.Assert;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Arrays;

/**
 *  @author dsl
 */
public class GraphTestUtil {
  static <E> void assertIteratorsEqual(Iterator<E> expected, Iterator<E> found) {
    for (; expected.hasNext(); ) {
      Assert.assertTrue(found.hasNext());
      Assert.assertEquals(expected.next(), found.next());
    }
    Assert.assertFalse(found.hasNext());
  }

  static class EmptyNodeIterator implements Iterator<TestNode> {
    public static EmptyNodeIterator INSTANCE = new EmptyNodeIterator();
    public boolean hasNext() {
      return false;
    }

    public TestNode next() {
      throw new NoSuchElementException();
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  public static Iterator<TestNode> iteratorOfArray(TestNode[] array) {
    return Arrays.asList(array).iterator();
  }
}
