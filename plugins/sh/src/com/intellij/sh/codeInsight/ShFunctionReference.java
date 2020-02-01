// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveState;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.codeInsight.processor.ShFunctionNewProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFunctionReference extends PsiReferenceBase<PsiElement> {
  private static final ShFunctionResolver RESOLVER = new ShFunctionResolver();

  public ShFunctionReference(@NotNull PsiElement element) {
    super(element, TextRange.create(0, element.getTextLength()));
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    ShFunctionNewProcessor functionProcessor = new ShFunctionNewProcessor(myElement.getText());
    PsiTreeUtil.treeWalkUp(functionProcessor, myElement , null, ResolveState.initial());
    return functionProcessor.getFunction();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ShFunctionReference that = (ShFunctionReference)o;
    if (!myElement.equals(that.getElement())) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }
}
