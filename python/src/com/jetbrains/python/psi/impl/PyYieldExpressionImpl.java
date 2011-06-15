package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyYieldExpressionImpl extends PyElementImpl implements PyYieldExpression {
  public PyYieldExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyYieldExpression(this);
  }

  @Override
  public PyExpression getExpression() {
    return childToPsi(PyElementTypes.EXPRESSIONS, 0);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context) {
    final PyExpression e = getExpression();
    return e != null ? e.getType(context) : null;
  }
}
