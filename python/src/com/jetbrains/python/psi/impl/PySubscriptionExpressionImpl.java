package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.types.PyCollectionType;
import com.jetbrains.python.psi.types.PySubscriptableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
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

  public PyType getType(@NotNull TypeEvalContext context) {
    final PyExpression indexExpression = getIndexExpression();
    if (indexExpression != null) {
      final PyType type = context.getType(getOperand());
      if (type instanceof PySubscriptableType) {
        return ((PySubscriptableType)type).getElementType(indexExpression, context);
      }
      if (type instanceof PyCollectionType) {
        return ((PyCollectionType) type).getElementType(context);
      }
    }
    return null;
  }
}
