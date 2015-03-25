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
      PyExpression expression = (PyExpression)child.getPsi();
      ASTNode exprNode = expression.getNode();
      ASTNode next = PyPsiUtils.getNextComma(exprNode);
      ASTNode prev = PyPsiUtils.getPrevComma(exprNode);
      super.deleteChildInternal(child);
      if (next != null) {
        deleteChildInternal(next);
      }
      else if (prev != null) {
        deleteChildInternal(prev);
      }
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  @NotNull
  public PyExpression[] getElements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }
}
