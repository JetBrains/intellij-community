/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

    int minIndent = Integer.MAX_VALUE;
    for (int i = 0; i < indentArray.length; i++) {
      minIndent = Math.min(minIndent, indentArray[i]);
    }

    if (indentArray[0] == minIndent) {
      for (int i = 0; i < indentArray.length; i++) {
        indentArray[i] -= minIndent;
      }
      return;
    }

    int indent = indentArray[0];
    int lastIndent = Integer.MAX_VALUE;
    boolean lastIndented = false;
    boolean insideMultiline = false;
    boolean shouldSkipNext = false;

    Map<String, Integer> openedBrackets = new HashMap<String, Integer>();
    openedBrackets.put("(", 0);
    openedBrackets.put("[", 0);
    openedBrackets.put("{", 0);

    for (int i = 0; i < indentArray.length; i++) {
      if (!StringUtil.isEmpty(lines.get(i))) {

        if(i > 0 && shouldSkipNext(lines.get(i - 1), openedBrackets)) {
          shouldSkipNext = true;
        }

        openedBrackets = countOpenedBrackets(openedBrackets, lines.get(i));

        if (shouldSkipNext || shouldSkip(lines.get(i), insideMultiline)) {
          if(!lines.get(i).startsWith("#")){
            shouldSkipNext = false;
          }
          insideMultiline = insideMultiline ^ startOrEndNewMultiline(lines.get(i));
          indentArray[i] -= indent;
          continue;
        }

        if (indentArray[i] < indent || indentArray[i] > lastIndent && !lastIndented) {
          indent = indentArray[i];
        }
        lastIndent = indentArray[i];
        indentArray[i] -= indent;
        if (shouldIndent(lines.get(i))) {
          lastIndented = true;
        }
        else {
          lastIndented = false;
        }
      }
    }
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

  public static boolean shouldSkipNext(@NotNull String line, Map<String, Integer> opened) {
    line = stripComments(line);
    return line.endsWith("\\") || insideBrackets(opened);
  }

  public static boolean shouldSkip(@NotNull String line, boolean insideMultiline) {
    return  insideMultiline || (insideMultiline ^ startOrEndNewMultiline(line)) || line.startsWith("#");
  }

  public static boolean startOrEndNewMultiline(String line){
    return PyConsoleUtil.isSingleQuoteMultilineStarts(line) || PyConsoleUtil.isDoubleQuoteMultilineStarts(line);
  }

  public static boolean insideBrackets(Map<String, Integer> opened){
    for(String bracket : opened.keySet()){
      if(opened.get(bracket) > 0) {
        return true;
      }
    }
    return false;
  }

  public static Map<String, Integer> countOpenedBrackets(Map<String, Integer> opened, String line){
    for(String open :  pythonBrackets.keySet()){
      opened.put(open, opened.get(open) + openedBracketsCountChange(line, open));
    }
    return opened;
  }

  public static int openedBracketsCountChange(String line, String open) {
    if(pythonBrackets.keySet().contains(open)){
      String close = pythonBrackets.get(open);
      return StringUtil.getOccurrenceCount(line, open) -  StringUtil.getOccurrenceCount(line, close) +
             StringUtil.getOccurrenceCount(line, "\\" + close) - StringUtil.getOccurrenceCount(line, "\\" + open);
    }  else {
      throw new UnsupportedOperationException("\"" + open + "\" not a parenthesis.");
    }
  }
}
