package com.intellij.util.containers;

import com.intellij.util.Assertion;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.Iterator;

public class CoModifiableListTest extends junit.framework.TestCase {
  private final Assertion CHECK = new Assertion();
  private final ArrayList mySourceList = new ArrayList();
  private final com.intellij.util.containers.CoModifiableList myList = new com.intellij.util.containers.CoModifiableList(mySourceList);

  public void testAdd() {
    myList.add("1");
    myList.add("2");
    checkElements(new Object[]{"1", "2"});
  }


  public void testInnerIterator() {
    mySourceList.add("a1");
    mySourceList.add("b2");
    mySourceList.add("a2");
    myList.forEach(new CoModifiableList.InnerIterator() {
      public void process(Object object, Iterator iterator) {
        if (StringUtil.startsWithChar(object.toString(), 'b')) iterator.remove();
      }
    });
    checkElements(new Object[]{"a1", "a2"});
  }

  public void testAddDuringItarating() {
    myList.add("1");
    myList.add("2");
    final int[] count = new int[] { 0 };
    myList.forEach(new CoModifiableList.InnerIterator() {
      public void process(Object object, Iterator iterator) {
        count[0]++;
        myList.add("new" + object);
      }
    });
    junit.framework.Assert.assertEquals(2, count[0]);
    checkElements(new Object[]{"1", "2", "new1", "new2"});
  }

  public void testRemoveDuringItarating() {
    myList.add("1");
    myList.add("2");
    final int[] count = new int[] { 0 };
    myList.forEach(new CoModifiableList.InnerIterator() {
      public void process(Object object, Iterator iterator) {
        count[0]++;
        if (object.equals("2")) {
          iterator.remove();
        }
      }
    });
    junit.framework.Assert.assertEquals(2, count[0]);
    checkElements(new Object[]{"1"});
  }

  private void checkElements(Object[] expected) {
    CHECK.compareAll(expected, mySourceList);
    CHECK.compareAll(expected, myList);
  }
}
