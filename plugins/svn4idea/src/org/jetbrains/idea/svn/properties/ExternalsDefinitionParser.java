// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.idea.svn.SvnBundle.message;

/**
 * @author Konstantin Kolosovsky.
 */
public final class ExternalsDefinitionParser {

  /**
   * Parses "svn:externals" property in format starting from svn 1.5.
   *
   * @return map of externals definitions: key - relative directory, value - corresponding complete externals definition.
   */
  @NotNull
  public static Map<String, String> parseExternalsProperty(@NotNull String externals) throws SvnBindException {
    HashMap<String, String> map = new HashMap<>();

    for (String external : StringUtil.splitByLines(externals, true)) {
      map.put(parseRelativeDirectory(external), external);
    }

    return map;
  }

  /**
   * Parses relative directory from externals definition (in format starting from svn 1.5). Restrictions for relative directory:
   * - is at the end of externals definition separated from other parameters by ' ' char
   * - could be quoted with '"' char
   * - certain chars could be escaped with '\' char
   */
  @NotNull
  public static String parseRelativeDirectory(@NotNull String s) throws SvnBindException {
    s = s.trim();

    int length = s.length();
    String result;

    if (isUnescapedQuote(s, length - 1)) {
      int index = lastUnescapedIndexOf(s, length - 1, '"');
      assertIndex(s, index, message("error.could.not.find.start.quote"));
      result = s.substring(index + 1, length - 1);
    }
    else {
      int index = lastUnescapedIndexOf(s, length, ' ');
      assertIndex(s, index, message("error.could.not.find.separating.space"));
      result = s.substring(index + 1);
    }

    return unescape(result);
  }

  private static void assertIndex(@NlsSafe @NotNull String s, int index, @DialogMessage @NotNull String message) throws SvnBindException {
    if (index < 0) {
      throw new SvnBindException(message + " - " + s);
    }
  }

  @NotNull
  private static String unescape(@NotNull String s) {
    return s.replace("\\", "");
  }

  /**
   * "from" index is excluded.
   */
  private static int lastUnescapedIndexOf(@NotNull String s, int from, char c) {
    int result = from;

    do {
      result = s.lastIndexOf(c, result - 1);
    }
    while (result != -1 && !isUnescaped(s, result, c));

    return result;
  }

  private static boolean isUnescapedQuote(@NotNull String s, int index) {
    return isUnescaped(s, index, '"');
  }

  private static boolean isUnescaped(@NotNull String s, int index, char c) {
    return StringUtil.isChar(s, index, c) && !StringUtil.isChar(s, index - 1, '\\');
  }
}
