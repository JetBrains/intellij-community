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
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Contains various methods for manipulation on indentation found in arbitrary text and individual lines: 
 * <ul>
 *   <li>calculating actual and expected indentation</li>
 *   <li>finding common indentation of several lines</li>
 *   <li>replacing and removing indentation of multiple lines</li>
 * </ul>
 *
 * It indented to be used primarily when one needs to modify content of Python files on document level and preserve valid block structure.  
 * Note that in most scenarios accurate indentation consistent with the code style settings is provided by automatic formatting pass 
 * that is performed each time you modify PSI tree directly.
 * 
 * @author Mikhail Golubev
 */
public class PyIndentUtil {
  @NonNls public static final String TWO_SPACES = "  ";
  @NonNls public static final String FOUR_SPACES = "    ";

  private PyIndentUtil() {
  }

  /**
   * Returns indentation size as number of characters <tt>' '</tt> and <tt>'\t'</tt> in the beginning of a line. 
   * It doesn't perform any expansion of tabs.
   */
  public static int getLineIndentSize(@NotNull CharSequence line) {
    int stop;
    for (stop = 0; stop < line.length(); stop++) {
      final char c = line.charAt(stop);
      if (!(c == ' ' || c == '\t')) {
        break;
      }
    }
    return stop;
  }

  @NotNull
  public static String getLineIndent(@NotNull String line) {
    return line.substring(0, getLineIndentSize(line));
  }

  /**
   * Useful version of {@link #getLineIndent(String)} for custom character sequences like {@link com.jetbrains.python.toolbox.Substring}.
   */
  @NotNull
  public static CharSequence getLineIndent(@NotNull CharSequence line) {
    return line.subSequence(0, getLineIndentSize(line));
  }

  @NotNull
  public static String getElementIndent(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiFile) {
      return "";
    }
    final PyStatementList statementList = getAnchorStatementList(anchor);
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
      return getExpectedBlockIndent(statementList);
    }
  }

  @NotNull
  private static String getExpectedBlockIndent(@NotNull PyStatementList anchor) {
    final String indentStep = getIndentFromSettings(anchor.getProject());
    final PyStatementList parentBlock = PsiTreeUtil.getParentOfType(anchor, PyStatementList.class, true);
    if (parentBlock != null) {
      return getElementIndent(parentBlock) + indentStep;
    }
    return indentStep;
  }

  @Nullable
  private static PyStatementList getAnchorStatementList(@NotNull PsiElement element) {
    PyStatementList statementList = null;
    // First whitespace right before the statement list (right after ":")
    if (element instanceof PsiWhiteSpace) {
      statementList = as(element.getNextSibling(), PyStatementList.class);
    }
    if (statementList == null) {
      statementList = PsiTreeUtil.getParentOfType(element, PyStatementList.class, false);
    }
    return statementList;
  } 

  private static int getExpectedElementIndentSize(@NotNull PsiElement anchor) {
    int depth = 0;
    PyStatementList block = getAnchorStatementList(anchor);
    while (block != null) {
      depth += 1;
      block = PsiTreeUtil.getParentOfType(block, PyStatementList.class);
    }
    return depth * getIndentSizeFromSettings(anchor.getProject());
  }

  public static boolean areTabsUsedForIndentation(@NotNull Project project) {
    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    return codeStyleSettings.useTabCharacter(PythonFileType.INSTANCE);
  }

  public static char getIndentCharacter(@NotNull Project project) {
    return areTabsUsedForIndentation(project) ? '\t' : ' ';
  }

  /**
   * Returns indentation size configured in the Python code style settings.
   * 
   * @see #getIndentFromSettings(Project) 
   */
  public static int getIndentSizeFromSettings(@NotNull Project project) {
    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    final CommonCodeStyleSettings.IndentOptions indentOptions = codeStyleSettings.getIndentOptions(PythonFileType.INSTANCE);
    return indentOptions.INDENT_SIZE;
  }

  /**
   * Returns indentation configured in the Python code style settings either as space character repeated number times specified there
   * or a single tab character if tabs are set to use for indentation.
   * 
   * @see #getIndentSizeFromSettings(Project) 
   * @see #areTabsUsedForIndentation(Project) 
   */
  @NotNull
  public static String getIndentFromSettings(@NotNull Project project) {
    final boolean useTabs = areTabsUsedForIndentation(project);
    return useTabs ? "\t" : StringUtil.repeatSymbol(' ', getIndentSizeFromSettings(project));
  }

  @NotNull
  public static List<String> removeCommonIndent(@NotNull Iterable<String> lines, boolean ignoreFirstLine) {
    return changeIndent(lines, ignoreFirstLine, "");
  }

  @NotNull
  public static String removeCommonIndent(@NotNull String s, boolean ignoreFirstLine) {
    final List<String> trimmed = removeCommonIndent(LineTokenizer.tokenizeIntoList(s, false, false), ignoreFirstLine);
    return StringUtil.join(trimmed, "\n");
  }

  @NotNull
  public static String changeIndent(@NotNull String s, boolean ignoreFirstLine, String newIndent) {
    final List<String> trimmed = changeIndent(LineTokenizer.tokenizeIntoList(s, false, false), ignoreFirstLine, newIndent);
    return StringUtil.join(trimmed, "\n");
  }

  /**
   * Note that all empty lines will be trimmed regardless of their actual indentation.
   */
  @NotNull
  public static List<String> changeIndent(@NotNull Iterable<String> lines, boolean ignoreFirstLine, final String newIndent) {
    final String oldIndent = findCommonIndent(lines, ignoreFirstLine);
    if (Iterables.isEmpty(lines)) {
      return Collections.emptyList();
    }

    final List<String> result = ContainerUtil.map(Iterables.skip(lines, ignoreFirstLine ? 1 : 0), line -> {
      if (StringUtil.isEmptyOrSpaces(line)) {
        return "";
      }
      else {
        return newIndent + line.substring(oldIndent.length());
      }
    });
    if (ignoreFirstLine) {
      return ContainerUtil.prepend(result, Iterables.get(lines, 0));
    }
    return result;
  }

  @NotNull
  public static String findCommonIndent(@NotNull String s, boolean ignoreFirstLine) {
    return findCommonIndent(LineTokenizer.tokenizeIntoList(s, false, false), ignoreFirstLine);
  }

  /**
   * Finds maximum common indentation of the given lines. Indentation of empty lines and lines containing only whitespaces is ignored unless
   * they're the only lines provided. In the latter case common indentation for such lines is returned. If mix of tabs and spaces was used
   * for indentation and any two of lines taken into account contain incompatible combination of these symbols, i.e. it's impossible to 
   * decide which one can be used as prefix for another, empty string is returned.
   *
   * @param ignoreFirstLine whether the first line should be considered (useful for multiline string literals)
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
      final String indent = getLineIndent(line);
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
    return getLineIndent(line);
  }
}
