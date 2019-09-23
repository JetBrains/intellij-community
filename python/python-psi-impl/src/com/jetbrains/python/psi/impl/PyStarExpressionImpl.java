// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyStarExpressionImpl extends PyElementImpl implements PyStarExpression {
  public PyStarExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  @Nullable
  public PyExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return null;
  }

  @Override
  public void acceptPyVisitor(PyElementVisitor visitor) {
    visitor.visitPyStarExpression(this);
  }

  @Override
  public boolean isAssignmentTarget() {
    return getExpression() instanceof PyTargetExpression;
  }

  @Override
  public boolean isUnpacking() {
    if (isAssignmentTarget()) {
      return false;
    }
    PsiElement parent = getParent();
    while (parent instanceof PyParenthesizedExpression) {
      parent = parent.getParent();
    }
    return parent instanceof PyTupleExpression || parent instanceof PyListLiteralExpression || parent instanceof PySetLiteralExpression;
  }
}
