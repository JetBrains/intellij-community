package com.jetbrains.python.documentation;

/**
 * User: catherine
 */
public class DocStringUtil {
  private DocStringUtil() {
  }

  public static String trimDocString(String s) {
    return s.trim()
            .replaceFirst("^((:py)?:class:`[~!]?|[A-Z]\\{)", "")
            .replaceFirst("(`|\\})?\\.?$", "");
  }
}
