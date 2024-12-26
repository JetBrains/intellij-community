// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.JavaRareRefactoringsBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class TypeCookViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myElements;

  TypeCookViewDescriptor(PsiElement[] elements) {
    myElements = elements;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return JavaRareRefactoringsBundle.message("type.cook.elements.header");
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return JavaRareRefactoringsBundle.message("declaration.s.to.be.generified", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
