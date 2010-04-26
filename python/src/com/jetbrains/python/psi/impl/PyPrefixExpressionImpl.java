package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyPrefixExpressionImpl extends PyElementImpl implements PyPrefixExpression {
  public PyPrefixExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyExpression getOperand() {
    return (PyExpression)childToPsiNotNull(PyElementTypes.EXPRESSIONS, 0);
  }

  @NotNull
  public PyElementType getOperationSign() {
    return (PyElementType)getNode().findChildByType(PyElementTypes.BINARY_OPS).getElementType();
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    return null;
  }
}
