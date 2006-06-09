package com.intellij.util.containers;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * @author max
 */
public class IntToIntArrayMap {
  private static final int[] EMPTY_INTS = new int[0];
  private TIntIntHashMap mySingle;
  private TIntObjectHashMap<IntArrayList> myMulti;


  public IntToIntArrayMap(int initialCapacity, float loadfactor) {
    mySingle = new TIntIntHashMap(initialCapacity, loadfactor);
    myMulti = new TIntObjectHashMap<IntArrayList>(initialCapacity, loadfactor);
  }

  public void addOccurence(int key, int value) {
    if (mySingle.containsKey(key)) {
      int old = mySingle.get(key);
      IntArrayList items = new IntArrayList(2);
      items.add(old);
      items.add(value);
      mySingle.remove(key);
      myMulti.put(key, items);
      return;
    }

    if (myMulti.containsKey(key)) {
      myMulti.get(key).add(value);
      return;
    }

    mySingle.put(key, value);
  }

  public void removeOccurence(int key, int value) {
    if (mySingle.containsKey(key)) {
      mySingle.remove(key);
      return;
    }

    IntArrayList items = myMulti.get(key);
    if (items != null) {
      int idx = items.indexOf(value);
      if (idx >= 0) {
        items.remove(idx);
        if (items.size() == 1) {
          mySingle.put(key, items.get(0));
          myMulti.remove(key);
        }
        else if (items.size() == 0) {
          myMulti.remove(key);
        }
      }
    }
  }

  public int[] get(int key) {
    if (mySingle.containsKey(key)) {
      return new int[]{mySingle.get(key)};
    }

    IntArrayList items = myMulti.get(key);
    if (items == null) return EMPTY_INTS;
    return items.toArray();
  }
}
