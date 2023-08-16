// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.Nullable;

/**
 * Represents Type Alias Statement added in <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeAliasStatement extends PyStatement, PsiNameIdentifierOwner, PyTypeParameterListOwner, PyTypedElement {

  @Nullable
  PyExpression getTypeExpression();
}
