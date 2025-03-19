// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.ast.PyAstKeywordArgument;
import org.jetbrains.annotations.Nullable;


public interface PyKeywordArgument extends PyAstKeywordArgument, PyExpression, PsiNamedElement, PsiExternalReferenceHost {
  @Override
  default @Nullable PyExpression getValueExpression() {
    return (PyExpression)PyAstKeywordArgument.super.getValueExpression();
  }
}
