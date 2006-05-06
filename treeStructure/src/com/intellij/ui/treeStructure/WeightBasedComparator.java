/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import java.util.Comparator;

public class WeightBasedComparator implements Comparator {

  public static final WeightBasedComparator INSTANCE = new WeightBasedComparator();

  public int compare(Object o1, Object o2) {
    SimpleNode first = (SimpleNode) o1;
    SimpleNode second = (SimpleNode) o2;

    if (first.getWeight() == second.getWeight()) {
      String s1 = first.toString();
      String s2 = second.toString();
      if (s1 == null) return s2 == null ? 0 : -1;
      if (s2 == null) return +1;
      return s1.compareToIgnoreCase(s2);
    }
    return second.getWeight() - first.getWeight();
  }
}
