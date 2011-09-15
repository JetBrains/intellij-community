package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;

/**
 * @author yole
 */
public class PyExpressionStatementImpl extends PyElementImpl implements PyExpressionStatement {
  public PyExpressionStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  public PyExpression getExpression() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyExpressionStatement(this);
  }
}
