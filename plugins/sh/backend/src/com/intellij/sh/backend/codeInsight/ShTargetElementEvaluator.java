// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.codeInsight;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.psi.PsiElement;
import com.intellij.sh.psi.ShFunctionDefinition;
import org.jetbrains.annotations.NotNull;

public class ShTargetElementEvaluator extends TargetElementEvaluatorEx2 {
  @Override
  public boolean isAcceptableNamedParent(@NotNull PsiElement parent) {
    return parent instanceof ShFunctionDefinition;
  }
}
