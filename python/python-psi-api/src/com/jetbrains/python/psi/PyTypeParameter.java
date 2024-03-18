// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstTypeParameter;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Type Parameter that can be a part of {@link PyTypeParameterList}<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeParameter extends PyAstTypeParameter, PyElement, PsiNameIdentifierOwner, PyTypedElement, StubBasedPsiElement<PyTypeParameterStub> {
  @Override
  @Nullable
  default PyExpression getBoundExpression() {
    return (PyExpression)PyAstTypeParameter.super.getBoundExpression();
  }
}
