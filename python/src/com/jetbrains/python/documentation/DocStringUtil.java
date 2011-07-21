package com.jetbrains.python.documentation;

/**
 * User: catherine
 */
public class DocStringUtil {
  private DocStringUtil() {
  }

  public static String trimDocString(String s) {
    return s.trim()
            .replaceFirst("^(:class:|:py:class:)", "")
            .trim()
            .replaceFirst("^`", "")
            .replaceFirst("(`|\\.)$", "");
  }
}
