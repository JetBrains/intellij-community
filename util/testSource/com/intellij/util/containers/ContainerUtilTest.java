package com.intellij.util.containers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class ContainerUtilTest extends junit.framework.TestCase {
  public void testFindInstanceOf() {
    Iterator<Object> iterator = Arrays.asList(new Object[]{new Integer(1), new ArrayList(), "1"}).iterator();
    String string = (String)com.intellij.util.containers.ContainerUtil.find(iterator, com.intellij.util.containers.FilteringIterator.instanceOf(String.class));
    junit.framework.Assert.assertEquals("1", string);
  }
}
