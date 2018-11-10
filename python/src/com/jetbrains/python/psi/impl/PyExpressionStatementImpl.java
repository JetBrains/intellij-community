// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyExpressionStatementImpl extends PyElementImpl implements PyExpressionStatement {
  public PyExpressionStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  @NotNull
  public PyExpression getExpression() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyExpressionStatement(this);
  }
}
