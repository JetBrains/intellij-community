package com.intellij.bash.shellcheck;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BashShellcheckInspection extends LocalInspectionTool implements ExternalAnnotatorBatchInspection {
  static final String SHORT_NAME = "ShellCheck";

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.NON_SWITCHABLE_ERROR;
  }

  @NotNull
  @Override
  public String getShortName() {
    return SHORT_NAME;
  }

  @NotNull
  @Override
  public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }
}