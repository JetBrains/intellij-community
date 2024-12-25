// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstExceptPart;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import org.jetbrains.annotations.Nullable;

public interface PyExceptPart extends PyAstExceptPart, PyElement, StubBasedPsiElement<PyExceptPartStub>, PyNamedElementContainer, PyStatementPart {
  PyExceptPart[] EMPTY_ARRAY = new PyExceptPart[0];

  @Override
  default @Nullable PyExpression getExceptClass() {
    return (PyExpression)PyAstExceptPart.super.getExceptClass();
  }

  @Override
  default @Nullable PyExpression getTarget() {
    return (PyExpression)PyAstExceptPart.super.getTarget();
  }
}
