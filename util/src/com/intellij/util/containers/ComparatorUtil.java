/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import java.util.Comparator;

public class ComparatorUtil {
  public static <Type, Aspect> Comparator<Type> compareBy(final Convertor<Type, Aspect> aspect, final Comparator<Aspect> comparator) {
    return new Comparator<Type>() {
      public int compare(Type element1, Type element2) {
        return comparator.compare(aspect.convert(element1), aspect.convert(element2));
      }
    };
  }

  public static <T extends Comparable<T>> T max(T o1, T o2) {
    return o1.compareTo(o2) >= 0 ? o1 : o2;
  }

  public static <T extends Comparable<T>> T min(T o1, T o2) {
    return o1.compareTo(o2) >= 0 ? o2 : o1;
  }

}
