package com.jetbrains.python.console;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Scanner;

/**
 * @author traff
 */
public class PyConsoleIndentUtil {
  private static final int TAB_INDENT = 4;

  private PyConsoleIndentUtil() {
  }

  public static String normalize(@NotNull String codeFragment) {
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

    shiftToParentOnUnindent(indentArray);

    return padOutput(lines, indentArray);
  }

  private static void shiftToParentOnUnindent(int[] indentArray) {
    int[] stack = new int[indentArray.length];
    int count = 0;
    int replace = -1;
    int replaceBy = -1;
    for (int i = 0; i < indentArray.length; i++) {
      if (indentArray[i] == replace) {
        indentArray[i] = replaceBy;
      }
      else {
        replace = -1;
        if (count == 0 || indentArray[i] > stack[count - 1]) {
          stack[count++] = indentArray[i];
        }
        if (count > 0 && indentArray[i] < stack[count - 1]) {
          do {
            count--;
          }
          while (count > 0 && stack[count - 1] > indentArray[i]);
          if (count > 0) {
            replace = indentArray[i];
            replaceBy = stack[count - 1];
            indentArray[i] = replaceBy;
          }
        }
      }
    }
  }

  private static void shiftLeftAll(int[] indentArray, List<String> lines) {
    if (indentArray.length == 0) {
      return;
    }
    int indent = indentArray[0];
    int lastIndent = Integer.MAX_VALUE;
    boolean lastIndented = false;
    for (int i = 0; i < indentArray.length; i++) {
      if (!StringUtil.isEmpty(lines.get(i))) {
        if (indentArray[i] < indent || indentArray[i] > lastIndent && !lastIndented) {
          indent = indentArray[i];
        }
        lastIndent = indentArray[i];
        indentArray[i] -= indent;
        if (lines.get(i).trim().endsWith(":")) {
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
}
