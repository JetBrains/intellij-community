package de.plushnikov.intellij.util;

import java.util.Collection;

public class StringUtils {
  public static boolean isEmpty(String str) {
    return str == null || str.length() == 0;
  }

  public static boolean isNotEmpty(String str) {
    return !isEmpty(str);
  }

  public static String join(Collection<String> list, String conjunction) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (String item : list) {
      if (first) {
        first = false;
      } else {
        sb.append(conjunction);
      }
      sb.append(item);
    }
    return sb.toString();
  }
}
