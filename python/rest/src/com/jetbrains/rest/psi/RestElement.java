// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.rest.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;

public class RestElement extends ASTWrapperPsiElement implements NavigatablePsiElement {

  public RestElement(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestElement:" + getNode().getElementType().toString();
  }

  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitRestElement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RestElementVisitor) {
      acceptRestVisitor(((RestElementVisitor)visitor));
    }
    else {
      super.accept(visitor);
    }
  }

}
