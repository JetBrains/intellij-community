package com.intellij.util.containers;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * @author max
 */
public class IntToIntSetMap {
  private static final int[] EMPTY_INTS = new int[0];
  private TIntIntHashMap mySingle;
  private TIntObjectHashMap<TIntHashSet> myMulti;


  public IntToIntSetMap(int initialCapacity, float loadfactor) {
    mySingle = new TIntIntHashMap(initialCapacity, loadfactor);
    myMulti = new TIntObjectHashMap<TIntHashSet>(initialCapacity, loadfactor);
  }

  public void addOccurence(int key, int value) {
    if (mySingle.containsKey(key)) {
      int old = mySingle.get(key);
      TIntHashSet items = new TIntHashSet(3);
      items.add(old);
      items.add(value);
      mySingle.remove(key);
      myMulti.put(key, items);
      return;
    }
    final TIntHashSet items = myMulti.get(key);
    if (items != null) {
      items.add(value);
      return;
    }
    mySingle.put(key, value);
  }

  public void removeOccurence(int key, int value) {
    if (mySingle.containsKey(key)) {
      mySingle.remove(key);
      return;
    }
    TIntHashSet items = myMulti.get(key);
    if (items != null) {
      items.remove(value);
      if (items.size() == 1) {
        mySingle.put(key, items.toArray()[0]);
        myMulti.remove(key);
      }
    }
  }

  public int[] get(int key) {
    if (mySingle.containsKey(key)) {
      return new int[]{mySingle.get(key)};
    }
    TIntHashSet items = myMulti.get(key);
    if (items == null) return EMPTY_INTS;
    return items.toArray();
  }
}
