package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
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
    return childToPsi(PyElementTypes.EXPRESSIONS, 0);
  }

  @Nullable
  public PyExpression getUpperBound() {
    return childToPsi(PyElementTypes.EXPRESSIONS, 1);
  }

  @Nullable
  public PyExpression getStride() {
    return childToPsi(PyElementTypes.EXPRESSIONS, 2);
  }
}
