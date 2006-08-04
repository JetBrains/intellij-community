/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import java.util.Arrays;

public class Comparing {
  private Comparing() { }

  public static <T> boolean  equal(T arg1, T arg2){
    if (arg1 == null || arg2 == null){
      return arg1 == arg2;
    }
    else if (arg1 instanceof Object[] && arg2 instanceof Object[]){
      Object[] arr1 = (Object[])arg1;
      Object[] arr2 = (Object[])arg2;
      return Arrays.equals(arr1, arr2);
    }
    else if (arg1 instanceof CharSequence && arg2 instanceof CharSequence) {
      return equal((CharSequence)arg1, (CharSequence)arg2, false);
    }
    else{
      return arg1.equals(arg2);
    }
  }

  public static <T> boolean equal(T[] arr1, T[] arr2){
    return Arrays.equals(arr1, arr2);
  }

  public static boolean equal(CharSequence s1, CharSequence s2) {
    return equal(s1, s2, false);
  }

  public static boolean equal(String arg1, String arg2) {
    return equal(arg1, arg2, false);
  }

  public static boolean equal(CharSequence s1, CharSequence s2, boolean ignoreCase) {
    if (s1 == s2) return true;
    if (s1 == null || s2 == null) return false;

    // Algorithm from String.regionMatches()
    int to = 0;
    int po = 0;

    if (s1.length() != s2.length()) return false;
    int len = s1.length();

    while (len-- > 0) {
      char c1 = s1.charAt(to++);
      char c2 = s2.charAt(po++);
      if (c1 == c2) {
        continue;
      }
      if (ignoreCase) {
        char u1 = Character.toUpperCase(c1);
        char u2 = Character.toUpperCase(c2);
        if (u1 == u2) continue;
        if (Character.toLowerCase(u1) == Character.toLowerCase(u2)) continue;
      }
      return false;
    }

    return true;
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

  public static <T extends Comparable<T>> int compare(final T name1, final T name2) {
    if (name1 == null) return name2 == null ? 0 : -1;
    if (name2 == null) return 1;
    return name1.compareTo(name2);
  }
}
