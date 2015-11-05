/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.google.common.collect.Iterables;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyIndentUtil {
  @NonNls public static final String TWO_SPACES = "  ";
  @NonNls public static final String FOUR_SPACES = "    ";

  private PyIndentUtil() {
  }

  public static int getLineIndentSize(@NotNull CharSequence line) {
    return getLineIndent(line).length();
  }

  @NotNull
  public static CharSequence getLineIndent(@NotNull CharSequence line) {
    int stop;
    for (stop = 0; stop < line.length(); stop++) {
      final char c = line.charAt(stop);
      if (!(c == ' ' || c == '\t')) {
        break;
      }
    }
    return line.subSequence(0, stop);
  }

  @NotNull
  public static String getElementIndent(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiFile) {
      return "";
    }
    final PyStatementList statementList = PsiTreeUtil.getParentOfType(anchor, PyStatementList.class, false);
    if (statementList == null) {
      return "";
    }
    final PsiElement prevSibling = statementList.getPrevSibling();
    final String whitespace = prevSibling instanceof PsiWhiteSpace ? prevSibling.getText() : "";
    final int i = whitespace.lastIndexOf("\n");
    if (i >= 0 && statementList.getStatements().length != 0) {
      return whitespace.substring(i + 1);
    }
    else {
      return getExpectedElementIndent(anchor);
    }
  }

  @NotNull
  public static String getExpectedElementIndent(@NotNull PsiElement anchor) {
    final String indentStep = getIndentFromSettings(anchor.getProject());
    final PyStatementList parentBlock = PsiTreeUtil.getParentOfType(anchor, PyStatementList.class, true);
    if (parentBlock != null) {
      return getElementIndent(parentBlock) + indentStep;
    }
    return anchor instanceof PyStatementList ? indentStep : "";
  }

  public static int getExpectedElementIndentSize(@NotNull PsiElement anchor) {
    int depth = 0;
    PyStatementList block = PsiTreeUtil.getParentOfType(anchor, PyStatementList.class, false);
    while (block != null) {
      depth += 1;
      block = PsiTreeUtil.getParentOfType(block, PyStatementList.class);
    }
    return depth * getIndentSizeFromSettings(anchor.getProject());
  }

  public static int getIndentSizeFromSettings(@NotNull Project project) {
    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    final CodeStyleSettings.IndentOptions indentOptions = codeStyleSettings.getIndentOptions(PythonFileType.INSTANCE);
    return indentOptions.INDENT_SIZE;
  }

  @NotNull
  public static String getIndentFromSettings(@NotNull Project project) {
    return StringUtil.repeatSymbol(' ', getIndentSizeFromSettings(project));
  }

  @NotNull
  public static List<String> removeCommonIndent(@NotNull Iterable<String> lines, boolean ignoreFirstLine) {
    return changeIndent(lines, ignoreFirstLine, "");
  }

  @NotNull
  public static String removeCommonIndent(@NotNull String s, boolean ignoreFirstLine) {
    final List<String> trimmed = removeCommonIndent(LineTokenizer.tokenizeIntoList(s, false), ignoreFirstLine);
    return StringUtil.join(trimmed, "\n");
  }

  @NotNull
  public static String changeIndent(@NotNull String s, boolean ignoreFirstLine, String newIndent) {
    final List<String> trimmed = changeIndent(LineTokenizer.tokenizeIntoList(s, false), ignoreFirstLine, newIndent);
    return StringUtil.join(trimmed, "\n");
  }



  /**
   * Not that all empty lines will be trimmed.
   */
  @NotNull
  public static List<String> changeIndent(@NotNull Iterable<String> lines, boolean ignoreFirstLine, final String newIndent) {
    final String oldIndent = findCommonIndent(lines, ignoreFirstLine);
    if (Iterables.isEmpty(lines)) {
      return Collections.emptyList();
    }

    final List<String> result = ContainerUtil.map(Iterables.skip(lines, ignoreFirstLine ? 1 : 0), new Function<String, String>() {
      @Override
      public String fun(String line) {
        if (StringUtil.isEmptyOrSpaces(line)) {
          return "";
        }
        else {
          return newIndent + line.substring(oldIndent.length());
        }
      }
    });
    if (ignoreFirstLine) {
      return ContainerUtil.prepend(result, Iterables.get(lines, 0));
    }
    return result;
  }

  /**
   * If lines include non-empty lines, all empty lines or lines that contain only spaces are ignored.
   * Otherwise (all line are empty) their common indent are returned as expected. If any two lines
   * have different indentation (e.g. one contains tab character and another doesn't), empty prefix
   * is returned.
   */
  @NotNull
  public static String findCommonIndent(@NotNull Iterable<String> lines, boolean ignoreFirstLine) {
    String minIndent = null;
    boolean allLinesEmpty = true;
    if (Iterables.isEmpty(lines)) {
      return "";
    }
    boolean hasBadEmptyLineIndent = false;
    for (String line : Iterables.skip(lines, ignoreFirstLine ? 1 : 0)) {
      final boolean lineEmpty = StringUtil.isEmptyOrSpaces(line);
      if (lineEmpty && !allLinesEmpty) {
        continue;
      }
      final String indent = (String)getLineIndent(line);
      if (minIndent == null || (!lineEmpty && allLinesEmpty) || minIndent.startsWith(indent)) {
        minIndent = indent;
      }
      else if (!indent.startsWith(minIndent)) {
        if (lineEmpty) {
          hasBadEmptyLineIndent = true;
        }
        else {
          return "";
        }
      }
      allLinesEmpty &= lineEmpty;
    }
    if (allLinesEmpty && hasBadEmptyLineIndent) {
      return "";
    }
    return StringUtil.notNullize(minIndent);
  }

  @NotNull
  public static String getLineIndent(@NotNull Document document, int lineNumber) {
    final TextRange lineRange = TextRange.create(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));
    final String line = document.getText(lineRange);
    return (String)getLineIndent(line);
  }
}
