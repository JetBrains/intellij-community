package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PySequenceExpressionImpl extends PyElementImpl {
  public PySequenceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getPsi() instanceof PyExpression) {
      final ASTNode commaNode = PyPsiUtils.getAdjacentComma(child);
      if (commaNode != null) {
        super.deleteChildInternal(commaNode);
      }
    }
    super.deleteChildInternal(child);
  }

  @NotNull
  public PyExpression[] getElements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }
}
