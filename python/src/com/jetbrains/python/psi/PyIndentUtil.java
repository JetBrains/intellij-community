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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyIndentUtil {
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
    if (i >= 0) {
      return whitespace.substring(i + 1);
    }
    else {
      return getExpectedElementIndent(anchor);
    }
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

  public static String getExpectedElementIndent(@NotNull PsiElement anchor) {
    return StringUtil.repeat(" ", getExpectedElementIndentSize(anchor));
  }
}
