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



public class TObjectIntHashMapTest extends junit.framework.TestCase {
  public void test() {
    gnu.trove.TObjectIntHashMap map = new gnu.trove.TObjectIntHashMap();
    map.trimToSize();
    for (int i = 0; i < 100; i++) {
      String key = String.valueOf(i);
      map.put(key, i);
      map.put(key+"a", i);
    }
    for (int i = 0; i < 100; i++) {
      String key = String.valueOf(i);
      junit.framework.Assert.assertEquals(i, map.get(key));
      junit.framework.Assert.assertEquals(i, map.get(key+"a"));
    }
    junit.framework.Assert.assertEquals(200, map.size());
  }
}
