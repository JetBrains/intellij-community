package com.intellij.bash.shellcheck;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BashShellcheckInspection extends LocalInspectionTool implements ExternalAnnotatorBatchInspection {
  static final String SHORT_NAME = "Shellcheck";

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