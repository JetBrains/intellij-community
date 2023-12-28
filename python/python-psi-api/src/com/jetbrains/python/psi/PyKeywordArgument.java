// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.ast.PyAstKeywordArgument;
import org.jetbrains.annotations.Nullable;


public interface PyKeywordArgument extends PyAstKeywordArgument, PyExpression, PsiNamedElement {
  @Override
  @Nullable
  default PyExpression getValueExpression() {
    return (PyExpression)PyAstKeywordArgument.super.getValueExpression();
  }
}
