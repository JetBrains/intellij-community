package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import junit.framework.TestCase;

import java.util.Arrays;

import com.intellij.util.containers.Enumerator;

/**
 * @author dyoma
 */
public class EnumeratorTest extends TestCase {
  public void test() {
    Enumerator enumerator = new Enumerator(10, TObjectHashingStrategy.CANONICAL);
    int[] indecies = enumerator.enumerate(new Object[]{"a", "b", "a"});
    assertTrue(Arrays.equals(new int[]{1, 2, 1}, indecies));
    indecies = enumerator.enumerate(new Object[]{"a", "c", "b"});
    assertTrue(Arrays.equals(new int[]{1, 3, 2}, indecies));
  }
}
