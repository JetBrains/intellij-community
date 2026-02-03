// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface QualifiedResolveResult extends ResolveResult {

  @NotNull
  QualifiedResolveResult EMPTY = new QualifiedResolveResult() {

    @Override
    public @NotNull List<PyExpression> getQualifiers() {
      return Collections.emptyList();
    }

    @Override
    public boolean isImplicit() {
      return false;
    }

    @Override
    public @Nullable PsiElement getElement() {
      return null;
    }

    @Override
    public boolean isValidResult() {
      return false;
    }
  };

  /**
   * @return the qualifiers which were collected while following assignments chain.
   *
   * @see com.jetbrains.python.psi.PyReferenceExpression#followAssignmentsChain(PyResolveContext)
   * @see com.jetbrains.python.psi.PyReferenceExpression#multiFollowAssignmentsChain(PyResolveContext)
   */
  @NotNull
  List<PyExpression> getQualifiers();

  /**
   * @return true iff the resolve result is implicit, that is, not exact but by divination and looks reasonable. 
   */
  boolean isImplicit();
}
