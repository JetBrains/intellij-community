package com.intellij.sh.run;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface ShRunnerAdditionalCondition {
  ExtensionPointName<ShRunnerAdditionalCondition> EP = ExtensionPointName.create("com.intellij.runMarkerContributionAdditionalCondition");

  boolean isRunningProhibitedForElement(@NotNull PsiElement element);
  boolean isRunningProhibitedForFile(@NotNull PsiFile file);
}
