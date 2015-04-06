package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.ArrayUtil;
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
    if (ArrayUtil.contains(child.getPsi(), getElements())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
    }
    super.deleteChildInternal(child);
  }

  @NotNull
  public PyExpression[] getElements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }
}
