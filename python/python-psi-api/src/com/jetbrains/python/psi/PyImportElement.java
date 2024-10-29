// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstImportElement;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public interface PyImportElement extends PyAstImportElement, PyElement, PyImportedNameDefiner, StubBasedPsiElement<PyImportElementStub> {
  @Override
  @Nullable
  default PyReferenceExpression getImportReferenceExpression() {
    return (PyReferenceExpression)PyAstImportElement.super.getImportReferenceExpression();
  }

  @Override
  @Nullable
  default PyTargetExpression getAsNameElement() {
    return (PyTargetExpression)PyAstImportElement.super.getAsNameElement();
  }

  @Override
  default PyStatement getContainingImportStatement() {
    return (PyStatement)PyAstImportElement.super.getContainingImportStatement();
  }

  @Nullable
  PsiElement getElementNamed(String name, boolean resolveImportElement);

  /**
   * @deprecated Use {@link #multiResolve()} instead.
   */
  @ApiStatus.Internal
  @Deprecated
  @Nullable
  PsiElement resolve();

  /**
   * Resolves the import element to the elements being imported.
   */
  @NotNull
  List<RatedResolveResult> multiResolve();
}
