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

  public void testChainIterableByLists() {
    List<String> list1 = Arrays.asList("foo", "bar", "baz");
    List<String> list2 = Arrays.asList("ichi", "ni", "san");
    List<String> list3 = Arrays.asList("a", "s", "d", "f");
    List<String> all = new ArrayList<>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterable<String> tested = new ChainIterable<>(list1).add(list2).add(list3);
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
    List<String> all = new ArrayList<>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterable<String> tested = new ChainIterable<>(list1).add(list2).add(list3);
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
    List<String> all = new ArrayList<>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterable<String> tested = new ChainIterable<>(list1).add(list2).add(list3);
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
    List<String> all = new ArrayList<>();
    all.addAll(list1);
    all.addAll(list2);
    all.addAll(list3);
    ChainIterable<String> tested = new ChainIterable<>(list1).add(list2).add(list3);
    int count = 0;
    for (String what : tested) {
      assertEquals(all.get(count), what);
      count += 1;
    }
    assertEquals(all.size(), count);
  }

  public void testToStringDoesntExhaustIterator() {
    final ChainIterable<String> initial = new ChainIterable<>();
    initial.addItem("foo");
    assertEquals("foo", initial.toString());;
    assertEquals("foo", initial.toString());
  }
}
