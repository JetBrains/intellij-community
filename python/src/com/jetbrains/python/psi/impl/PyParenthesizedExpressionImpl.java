package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.types.PyType;

/**
 * @author yole
 */
public class PyParenthesizedExpressionImpl extends PyElementImpl implements PyParenthesizedExpression {
  public PyParenthesizedExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyParenthesizedExpression(this);
  }

  public PyExpression getContainedExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public PyType getType() {
    final PyExpression expr = getContainedExpression();
    return expr != null ? expr.getType() : null;
  }
}
