package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceItem;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PySliceItemImpl extends PyElementImpl implements PySliceItem {
  public PySliceItemImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  public PyExpression getLowerBound() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Nullable
  public PyExpression getUpperBound() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
  }

  @Nullable
  public PyExpression getStride() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 2);
  }
}
