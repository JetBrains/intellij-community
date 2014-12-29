/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author traff
 */
public class PyConsoleIndentUtil {
  private static final int TAB_INDENT = 4;

  private static final Map<String, String> pythonBrackets = ImmutableMap.of(
      "(", ")",
      "[", "]",
      "{", "}"
  );

  private PyConsoleIndentUtil() {
  }

  public static String normalize(@NotNull String codeFragment) {
    return normalize(codeFragment, 0);
  }

  public static String normalize(@NotNull String codeFragment, int addIndent) {
    Scanner s = new Scanner(codeFragment);

    List<String> lines = Lists.newArrayList();
    List<Integer> indents = Lists.newArrayList();
    while (s.hasNextLine()) {
      String line = s.nextLine();
      int indent = 0;
      for (char c : line.toCharArray()) {
        if (c == ' ') {
          indent++;
        }
        else if (c == '\t') {
          indent += TAB_INDENT;
        }
        else {
          break;
        }
      }
      if (!StringUtil.isEmpty(line)) {
        lines.add(line.trim());
        indents.add(indent);
      }
    }

    int[] indentArray = ArrayUtil.toIntArray(indents);

    shiftLeftAll(indentArray, lines);

    for (int i = 0; i < indentArray.length; i++) {
      indentArray[i] += addIndent;
    }

    return padOutput(lines, indentArray);
  }

  private static void shiftLeftAll(int[] indentArray, List<String> lines) {
    if (indentArray.length == 0) {
      return;
    }

    int minpos = arrayMinPosition(indentArray, indentArray.length);
    if (minpos == 0) {
      int minIndent = indentArray[minpos];
      shiftTailLeftOnLevel(indentArray, minIndent);
      return;
    }

    int prevMinPosition = indentArray.length;
    while (minpos != 0) {
      shiftTailLeftOnLevel(indentArray, minpos, prevMinPosition, indentArray[minpos]);
      prevMinPosition = minpos;
      minpos = arrayMinPosition(indentArray, minpos);
    }

    int minIndent = indentArray[minpos];
    for (int i = 0; indentArray[i] != 0; i++) {
      indentArray[i] -= minIndent;
    }
  }

  private static void shiftTailLeftOnLevel(int[] indentArray, int level) {
    shiftTailLeftOnLevel(indentArray, 0, indentArray.length, level);
  }

  private static void shiftTailLeftOnLevel(int[] indentArray, int upper, int bottom, int level) {
    for (int i = upper; i < bottom; i++) {
      if (indentArray[i] < level) {
        throw new IllegalStateException("Current indentation is less then subtracted level.");
      }
      indentArray[i] -= level;
    }
  }

  private static int arrayMinPosition(int[] indentArray, int border) {
    if (border < 1) {
      return -1;
    }

    int minPosition = 0;
    for (int i = 0; i < border; i++) {
      if (indentArray[minPosition] > indentArray[i]) {
        minPosition = i;
      }
    }
    return minPosition;
  }

  private static String padOutput(List<String> lines, int[] indentArray) {
    int i = 0;
    StringBuilder result = new StringBuilder();
    for (String line : lines) {
      if (!StringUtil.isEmpty(line)) {
        line = Strings.padStart(line, indentArray[i] + line.length(), ' ');
      }
      i++;
      result.append(line);
      if (i < lines.size()) {
        result.append("\n");
      }
    }

    return result.toString();
  }

  public static boolean shouldIndent(@NotNull String line) {
    line = stripComments(line);
    return line.endsWith(":");
  }

  private static String stripComments(String line) {
    if (line.contains("#")) {
      line = line.substring(0, line.indexOf("#")); //strip comments
      line = line.trim();
    }
    return line;
  }
}
