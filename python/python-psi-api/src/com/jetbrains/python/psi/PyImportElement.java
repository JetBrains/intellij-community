// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public interface PyImportElement extends PyElement, PyImportedNameDefiner, StubBasedPsiElement<PyImportElementStub> {
  @Nullable
  PyReferenceExpression getImportReferenceExpression();

  @Nullable
  QualifiedName getImportedQName();

  @Nullable
  PyTargetExpression getAsNameElement();

  @Nullable
  String getAsName();

  /**
   * @return name under which the element is visible, that is, "as name" is there is one, or just name.
   */
  @Nullable
  String getVisibleName();

  PyStatement getContainingImportStatement();
  
  @Nullable
  PsiElement getElementNamed(String name, boolean resolveImportElement);

  /**
   * @deprecated Use {@link #multiResolve()} instead.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  PsiElement resolve();

  /**
   * Resolves the import element to the elements being imported.
   */
  @NotNull
  List<RatedResolveResult> multiResolve();
}
