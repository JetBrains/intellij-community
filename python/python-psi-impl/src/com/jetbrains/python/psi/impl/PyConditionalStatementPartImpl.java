// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;

public abstract class PyConditionalStatementPartImpl extends PyStatementPartImpl implements PyConditionalStatementPart {
  public PyConditionalStatementPartImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyExpression getCondition() {
    ASTNode n = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    if (n != null) {
      return (PyExpression)n.getPsi();
    }
    return null;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyConditionalStatementPart(this);
  }
}
