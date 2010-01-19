package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceExpression;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PySliceExpressionImpl extends PyElementImpl implements PySliceExpression {
  public PySliceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    return getOperand().getType();
  }

  public PyExpression getOperand() {
    return childToPsiNotNull(PyElementTypes.EXPRESSIONS, 0);
  }

  public PyExpression getLowerBound() {
    return childToPsiNotNull(PyElementTypes.EXPRESSIONS, 1);
  }

  public PyExpression getUpperBound() {
    return childToPsi(PyElementTypes.EXPRESSIONS, 2);
  }

  @Nullable
  public PyExpression getStride() {
    return childToPsi(PyElementTypes.EXPRESSIONS, 3);
  }
}
