// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Describes 'raise' statement.
 */
@ApiStatus.Internal
public class PyRaiseStatementImpl extends PyElementImpl implements PyRaiseStatement {
  public PyRaiseStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyRaiseStatement(this);
  }

  @Override
  public PyExpression @NotNull [] getExpressions() {
    final PyExpression[] expressions = PsiTreeUtil.getChildrenOfType(this, PyExpression.class);
    return expressions != null ? expressions : PyExpression.EMPTY_ARRAY;
  }
}
