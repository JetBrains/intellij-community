package de.plushnikov.intellij.lombok;

public class StringUtils {
  public static String decapitalize(final String s) {
    if (s == null) return "";
    return s.substring(0, 1).toLowerCase() + s.substring(1);
  }

  public static String capitalize(final String s) {
    if (s == null) return "";
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }
}
