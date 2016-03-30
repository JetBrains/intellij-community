/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.yaml.lexer;

import com.intellij.openapi.util.text.StringUtil;

public class YAMLGrammarCharUtil {
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
