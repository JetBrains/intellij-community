// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyReturnStatementImpl extends PyElementImpl implements PyReturnStatement {
  public PyReturnStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyReturnStatement(this);
  }

  @Override
  @Nullable
  public PyExpression getExpression() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }
}
