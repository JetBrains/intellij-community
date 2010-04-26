package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyConditionalExpressionImpl extends PyElementImpl implements PyConditionalExpression {
  public PyConditionalExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    return null;
  }
}
