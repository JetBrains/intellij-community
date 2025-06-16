package com.intellij.sh.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sh.run.ShRunnerAdditionalCondition;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

final class PythonFileMarkerContributionAdditionalCondition implements ShRunnerAdditionalCondition {
  @Override
  public boolean isRunningProhibitedForElement(@NotNull PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    return psiFile instanceof PyFile;
  }

  @Override
  public boolean isRunningProhibitedForFile(@NotNull PsiFile file) {
    return file instanceof PyFile;
  }
}