// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.FileContentUtil;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public final class PythonCodeStyleServiceImpl extends PythonCodeStyleService {
  @Override
  public boolean isSpaceAroundEqInKeywordArgument(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT;
  }

  @Override
  public boolean isTabIndentation(@NotNull PsiFile file) {
    return CodeStyle.getIndentOptions(file).USE_TAB_CHARACTER;
  }

  @Override
  public int getIndentSize(@NotNull PsiFile file) {
    return CodeStyle.getIndentSize(file);
  }

  @Override
  public int getTabSize(@NotNull PsiFile file) {
    return CodeStyle.getIndentOptions(file).TAB_SIZE;
  }

  @Override
  public boolean isOptimizeImportsSortedByTypeFirst(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST;
  }

  @Override
  public boolean isOptimizeImportsAlwaysSplitFromImports(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS;
  }

  @Override
  public boolean isOptimizeImportsCaseSensitiveOrder(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER;
  }

  @Override
  public boolean isOptimizeImportsSortNamesInFromImports(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS;
  }

  @Override
  public boolean isOptimizeImportsSortImports(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_SORT_IMPORTS;
  }

  @Override
  public void reparseOpenEditorFiles(@NotNull Project project) {
    FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
  }

  @Override
  public void setSpaceAroundEqInKeywordArgument(@NotNull Project project, boolean enabled) {
    CodeStyle.getSettings(project).getCustomSettings(PyCodeStyleSettings.class).SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT = enabled;
  }
}
