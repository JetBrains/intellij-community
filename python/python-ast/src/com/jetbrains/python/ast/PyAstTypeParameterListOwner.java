// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * PSI element that can contain {@link PyAstTypeParameterList}
 * which was added in <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
@ApiStatus.Experimental
public interface PyAstTypeParameterListOwner extends PsiElement {

  @Nullable
  default PyAstTypeParameterList getTypeParameterList() {
    return null;
  }
}
