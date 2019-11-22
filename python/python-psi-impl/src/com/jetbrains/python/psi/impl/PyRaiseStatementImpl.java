// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Describes 'raise' statement.
 */
public class PyRaiseStatementImpl extends PyElementImpl implements PyRaiseStatement {
  public PyRaiseStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyRaiseStatement(this);
  }

  @Override
  @NotNull
  public PyExpression[] getExpressions() {
    final PyExpression[] expressions = PsiTreeUtil.getChildrenOfType(this, PyExpression.class);
    return expressions != null ? expressions : PyExpression.EMPTY_ARRAY;
  }
}
