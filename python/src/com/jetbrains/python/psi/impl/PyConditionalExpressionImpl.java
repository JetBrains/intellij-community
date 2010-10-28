package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PyConditionalExpressionImpl extends PyElementImpl implements PyConditionalExpression {
  public PyConditionalExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    final PyExpression falsePart = getFalsePart();
    if (falsePart == null) {
      return null;
    }
    return PyUnionType.union(context.getType(getTruePart()), context.getType(falsePart));
  }

  @Override
  public PyExpression getTruePart() {
    final List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
    return expressions.get(0);
  }

  @Override
  public PyExpression getCondition() {
    final List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
    return expressions.get(1);
  }

  @Override
  public PyExpression getFalsePart() {
    final List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
    return expressions.size() == 3 ? expressions.get(2) : null;
  }
}
