// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Type Parameter that can be a part of {@link PyTypeParameterList}<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeParameter extends PyElement, PsiNameIdentifierOwner, PyTypedElement, StubBasedPsiElement<PyTypeParameterStub> {

  @Nullable
  PyExpression getBoundExpression();

  /**
   * Returns the text of the bound expression of the type parameter
   * <p>
   * The text is taken from stub if the stub is presented.
   */
  @Nullable
  String getBoundExpressionText();
}
