/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import java.util.Arrays;

public class Comparing {
  public static boolean equal(Object arg1, Object arg2){
    if (arg1 == null || arg2 == null){
      return arg1 == arg2;
    }
    else if (arg1 instanceof Object[] && arg2 instanceof Object[]){
      Object[] arr1 = (Object[])arg1;
      Object[] arr2 = (Object[])arg2;
      return Arrays.equals(arr1, arr2);
    }
    else{
      return arg1.equals(arg2);
    }
  }

  public static boolean equal(String arg1, String arg2, boolean caseSensitive){
    if (arg1 == null || arg2 == null){
      return arg1 == arg2;
    }
    else{
      return caseSensitive ? arg1.equals(arg2) : arg1.equalsIgnoreCase(arg2);
    }
  }

  public static boolean strEqual(String arg1, String arg2){
    return strEqual(arg1, arg2, true);
  }

  public static boolean strEqual(String arg1, String arg2, boolean caseSensitive){
    return equal(arg1 == null ? "" : arg1, arg2 == null ? "" : arg2, caseSensitive);
  }

  public static int hashcode(Object obj) { return obj == null ? 0 : obj.hashCode(); }
  public static int hashcode(Object obj1, Object obj2) { return hashcode(obj1) ^ hashcode(obj2); }

}
