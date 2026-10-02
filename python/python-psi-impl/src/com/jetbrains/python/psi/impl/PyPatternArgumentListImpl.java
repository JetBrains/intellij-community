// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPatternArgumentList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public class PyPatternArgumentListImpl extends PyElementImpl implements PyPatternArgumentList, PsiListLikeElement {
  public PyPatternArgumentListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyPatternArgumentList(this);
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return getPatterns();
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getPatterns().contains(child.getPsi())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
    }
    super.deleteChildInternal(child);
  }
}
