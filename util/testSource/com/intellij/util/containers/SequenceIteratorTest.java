package com.intellij.util.containers;

import com.intellij.util.Assertion;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class SequenceIteratorTest extends TestCase {
  private final Assertion CHECK = new Assertion();

  public void testOneIterator() {
    Iterator<Object> iterator = iterate("1", "2");
    com.intellij.util.containers.SequenceIterator seq = new com.intellij.util.containers.SequenceIterator(new Iterator[]{iterator});
    CHECK.compareAll(new Object[]{"1", "2"}, com.intellij.util.containers.ContainerUtil.collect(seq));
  }

  public void testTwoNotEmpties() {
    Iterator<Object> seq = com.intellij.util.containers.SequenceIterator.create(iterate("1", "2"), iterate("3", "4"));
    CHECK.compareAll(new Object[]{"1", "2", "3", "4"}, com.intellij.util.containers.ContainerUtil.collect(seq));
  }

  public void testAllEmpty() {
    Assert.assertFalse(new com.intellij.util.containers.SequenceIterator(new Iterator[]{empty()}).hasNext());
    Assert.assertFalse(new com.intellij.util.containers.SequenceIterator(new Iterator[]{empty(), empty()}).hasNext());
  }

  public void testIntermediateEmpty() {
    com.intellij.util.containers.SequenceIterator seq = com.intellij.util.containers.SequenceIterator.create(iterate("1", "2"), empty(), iterate("3", "4"));
    CHECK.compareAll(new Object[]{"1", "2", "3", "4"}, com.intellij.util.containers.ContainerUtil.collect(seq));
  }

  public void testFirstEmpty() {
    com.intellij.util.containers.SequenceIterator seq = com.intellij.util.containers.SequenceIterator.create(empty(), iterate("1", "2"));
    CHECK.compareAll(new Object[]{"1", "2"}, com.intellij.util.containers.ContainerUtil.collect(seq));
  }

  private Iterator<Object> iterate(String first, String second) {
    return Arrays.asList(new Object[]{first, second}).iterator();
  }

  private Iterator empty() {
    return new ArrayList().iterator();
  }
}
