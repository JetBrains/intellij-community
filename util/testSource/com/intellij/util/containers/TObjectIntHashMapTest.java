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
