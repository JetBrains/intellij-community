/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.toolbox;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests all iterators and iterables.
 * User: dcheryasov
 * Date: Nov 20, 2009 3:42:51 AM
 */
public class IteratorsTest extends TestCase {

  public IteratorsTest() {
    super();
  }

  public void testRepeatIterable() {
    String value = "foo";
    RepeatIterable<String> tested = new RepeatIterable<String>(value);
    int count = 0;
    int times = 10;
    for (String what : tested) {
      assertEquals(value, what);
      count += 1;
      if (count >= times) break;
    }
    assertEquals(times, count);
  }

  public void testChainIterableByLists() {
    List<String> list1 = Arrays.asList("foo", "bar", "baz");
    List<String> list2 = Arrays.asList("ichi", "ni", "san");
    List<String> list3 = Arrays.asList("a", "s", "d", "f");
    List<String> all = new ArrayList<String>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterable<String> tested = new ChainIterable<String>(list1).add(list2).add(list3);
    int count = 0;
    for (String what : tested) {
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }

  public void testChainIterableEmptyFirst() {
    List<String> list1 = Arrays.asList();
    List<String> list2 = Arrays.asList("ichi", "ni", "san");
    List<String> list3 = Arrays.asList("a", "s", "d", "f");
    List<String> all = new ArrayList<String>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterable<String> tested = new ChainIterable<String>(list1).add(list2).add(list3);
    int count = 0;
    for (String what : tested) {
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }

  public void testChainIterableEmptyLast() {
    List<String> list1 = Arrays.asList("foo", "bar", "baz");
    List<String> list2 = Arrays.asList("ichi", "ni", "san");
    List<String> list3 = Arrays.asList();
    List<String> all = new ArrayList<String>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterable<String> tested = new ChainIterable<String>(list1).add(list2).add(list3);
    int count = 0;
    for (String what : tested) {
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }

  public void testChainIterableEmptyMiddle() {
    List<String> list1 = Arrays.asList("foo", "bar", "baz");
    List<String> list2 = Arrays.asList();
    List<String> list3 = Arrays.asList("a", "s", "d", "f");
    List<String> all = new ArrayList<String>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterable<String> tested = new ChainIterable<String>(list1).add(list2).add(list3);
    int count = 0;
    for (String what : tested) {
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }


  public void testChainIteratorByLists() {
    List<String> list1 = Arrays.asList("foo", "bar", "baz");
    List<String> list2 = Arrays.asList("ichi", "ni", "san");
    List<String> list3 = Arrays.asList("a", "s", "d", "f");
    List<String> all = new ArrayList<String>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterator<String> tested = new ChainIterator<String>(list1.iterator()).add(list2.iterator()).add(list3.iterator());
    int count = 0;
    String what;
    while (tested.hasNext()) {
      what = tested.next();
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }

  public void testChainIteratorEmptyFirst() {
    List<String> list1 = Arrays.asList();
    List<String> list2 = Arrays.asList("ichi", "ni", "san");
    List<String> list3 = Arrays.asList("a", "s", "d", "f");
    List<String> all = new ArrayList<String>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterator<String> tested = new ChainIterator<String>(list1.iterator()).add(list2.iterator()).add(list3.iterator());
    int count = 0;
    String what;
    while (tested.hasNext()) {
      what = tested.next();
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }

  public void testChainIteratorEmptyLast() {
    List<String> list1 = Arrays.asList("foo", "bar", "baz");
    List<String> list2 = Arrays.asList("ichi", "ni", "san");
    List<String> list3 = Arrays.asList();
    List<String> all = new ArrayList<String>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterator<String> tested = new ChainIterator<String>(list1.iterator()).add(list2.iterator()).add(list3.iterator());
    int count = 0;
    String what;
    while (tested.hasNext()) {
      what = tested.next();
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }

  public void testChainIteratorEmptyMiddle() {
    List<String> list1 = Arrays.asList("foo", "bar", "baz");
    List<String> list2 = Arrays.asList();
    List<String> list3 = Arrays.asList("a", "s", "d", "f");
    List<String> all = new ArrayList<String>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterator<String> tested = new ChainIterator<String>(list1.iterator()).add(list2.iterator()).add(list3.iterator());
    int count = 0;
    String what;
    while (tested.hasNext()) {
      what = tested.next();
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }


}
