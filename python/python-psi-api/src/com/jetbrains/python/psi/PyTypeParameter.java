// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Type Parameter that can be a part of {@link PyTypeParameterList}<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeParameter extends PyElement, PsiNameIdentifierOwner, PyTypedElement {

  @Nullable
  PyExpression getBoundExpression();

  @Nullable
  ASTNode getNameNode();
}
