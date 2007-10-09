/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
