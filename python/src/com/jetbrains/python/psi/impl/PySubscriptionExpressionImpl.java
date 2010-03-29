package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PySubscriptionExpressionImpl extends PyElementImpl implements PySubscriptionExpression {
  public PySubscriptionExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyExpression getOperand() {
    return childToPsiNotNull(PyElementTypes.EXPRESSIONS, 0);
  }

  @Nullable
  public PyExpression getIndexExpression() {
    return childToPsi(PyElementTypes.EXPRESSIONS, 1);
  }

  @Override
  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPySubscriptionExpression(this);
  }

  public PyType getType() {
    return null;
  }
}
