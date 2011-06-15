package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyKeywordArgumentImpl extends PyElementImpl implements PyKeywordArgument {
  public PyKeywordArgumentImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  public String getKeyword() {
    ASTNode node = getKeywordNode();
    return node != null ? node.getText() : null;
  }

  @Override
  public ASTNode getKeywordNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  public PyExpression getValueExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ": " + getKeyword();
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    final PyExpression e = getValueExpression();
    return e != null ? e.getType(context) : null;
  }
}
