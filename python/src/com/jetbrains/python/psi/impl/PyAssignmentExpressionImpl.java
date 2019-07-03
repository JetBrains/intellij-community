// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class PyAssignmentExpressionImpl extends PyElementImpl implements PyAssignmentExpression {

  public PyAssignmentExpressionImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  @Override
  public PyTargetExpression getTarget() {
    return notNullChild(ObjectUtils.tryCast(getFirstChild(), PyTargetExpression.class));
  }

  @Nullable
  @Override
  public PyExpression getAssignedValue() {
    return ObjectUtils.tryCast(getLastChild(), PyExpression.class);
  }

  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return context.getType(getTarget());
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAssignmentExpression(this);
  }
}
