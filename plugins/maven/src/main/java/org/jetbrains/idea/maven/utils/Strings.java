// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

public final class Strings {
  public static final @NonNls String WHITESPACE = " \t\n\r\f";

  public static List<String> tokenize(final String string, final String delim) {
    final List<String> tokens = new ArrayList<>();
    for (StringTokenizer tokenizer = new StringTokenizer(string, delim); tokenizer.hasMoreTokens(); ) {
      tokens.add(tokenizer.nextToken());
    }
    return tokens;
  }

  public static String detokenize(final Collection<String> list, final char delim) {
    final StringBuilder buffer = new StringBuilder();
    for (String goal : list) {
      if (!buffer.isEmpty()) {
        buffer.append(delim);
      }
      buffer.append(goal);
    }
    return buffer.toString();
  }

  public static String translateMasks(final Collection<String> masks) {
    final StringBuilder patterns = new StringBuilder();
    for (String mask : masks) {
      if (!patterns.isEmpty()) {
        patterns.append('|');
      }
      patterns.append(PatternUtil.convertToRegex(mask));
    }
    return patterns.toString();
  }

}
