package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 27.02.2010
 * Time: 14:27:28
 */
public class PyStarExpressionImpl extends PyElementImpl implements PyStarExpression {
  public PyStarExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  public PyExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return null;
  }

  public void acceptPyVisitor(PyElementVisitor visitor) {
    visitor.visitPyStarExpression(this);
  }
}
