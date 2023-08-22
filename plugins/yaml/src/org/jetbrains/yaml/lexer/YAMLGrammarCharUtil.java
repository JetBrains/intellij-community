// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.lexer;

import com.intellij.openapi.util.text.StringUtil;

public final class YAMLGrammarCharUtil {
  private static final String NS_INDICATORS = "-?:,\\[\\]\\{\\}#&*!|>'\\\"%@`";
  private static final String NS_FLOW_INDICATORS = ",[]{}";
  private static final String COMMON_SPACE_CHARS = "\n\r\t ";

  private YAMLGrammarCharUtil() {
  }

  public static boolean isIndicatorChar(char c) {
    return StringUtil.containsChar(NS_INDICATORS, c);
  }

  public static boolean isPlainSafe(char c) {
    return !isSpaceLike(c) && !StringUtil.containsChar(NS_FLOW_INDICATORS, c);
  }

  public static boolean isSpaceLike(char c) {
    return c == ' ' || c == '\t';
  }

  public static boolean isNonSpaceChar(char c) {
    return !StringUtil.containsChar(COMMON_SPACE_CHARS, c);
  }
}
