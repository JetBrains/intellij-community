// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;


public interface PyBinaryExpression extends PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

  PyExpression getLeftExpression();
  @Nullable PyExpression getRightExpression();

  @Nullable
  PyElementType getOperator();

  @Nullable
  PsiElement getPsiOperator();

  boolean isOperator(String chars);

  PyExpression getOppositeExpression(PyExpression expression)
      throws IllegalArgumentException;

  boolean isRightOperator(@Nullable PyCallable resolvedCallee);
}
