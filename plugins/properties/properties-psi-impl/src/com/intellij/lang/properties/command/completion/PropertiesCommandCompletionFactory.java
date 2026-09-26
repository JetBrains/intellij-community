// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.command.completion;

import com.intellij.codeInsight.completion.command.CommandCompletionFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

class PropertiesCommandCompletionFactory implements CommandCompletionFactory, DumbAware {

  @Override
  public boolean isApplicable(@NotNull PsiFile psiFile, int offset) {
    if (!(psiFile instanceof PropertiesFile)) return false;
    return true;
  }

  @Override
  public boolean isApplicableForHost(@NotNull PsiFile psiFile, int offset) {
    return false;
  }
}
