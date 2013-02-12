package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PySetCompExpressionImpl extends PyComprehensionElementImpl implements PySetCompExpression {
  public PySetCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyBuiltinCache.getInstance(this).getSetType();
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySetCompExpression(this);
  }
}
