/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.openapi.util.Pair;

/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingUtil {
  private static final char NUMBER_IN_ITERATION_PLACE_HOLDER = '$';

  private ZenCodingUtil() {
  }

  private static String replaceNumberMarkersBy(String s, String by) {
    StringBuilder builder = new StringBuilder(s.length());
    int j = -1;
    for (int i = 0, n = s.length(); i <= n; i++) {
      char c = i < n ? s.charAt(i) : 0;
      if (c == NUMBER_IN_ITERATION_PLACE_HOLDER) {
        if (j == -1) {
          j = i;
        }
      }
      else {
        if (j != -1) {
          for (int k = 0, m = i - j - by.length(); k < m; k++) {
            builder.append('0');
          }
          builder.append(by);
          j = -1;
        }
        if (i < n) {
          builder.append(c);
        }
      }
    }
    return builder.toString();
  }

  public static String getValue(Pair<String, String> pair, int numberInIteration) {
    String s = replaceNumberMarkersBy(pair.second, Integer.toString(numberInIteration + 1));
    return s.replace("\"", "&quot;");
  }
}
