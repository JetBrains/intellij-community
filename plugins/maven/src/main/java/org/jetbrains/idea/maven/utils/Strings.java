/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Vladislav.Kaznacheev
 */
public class Strings {
  @NonNls public static final String WHITESPACE = " \t\n\r\f";

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
      if (buffer.length() != 0) {
        buffer.append(delim);
      }
      buffer.append(goal);
    }
    return buffer.toString();
  }

  public static String translateMasks(final Collection<String> masks) {
    final StringBuilder patterns = new StringBuilder();
    for (String mask : masks) {
      if (patterns.length() != 0) {
        patterns.append('|');
      }
      patterns.append(PatternUtil.convertToRegex(mask));
    }
    return patterns.toString();
  }

}
