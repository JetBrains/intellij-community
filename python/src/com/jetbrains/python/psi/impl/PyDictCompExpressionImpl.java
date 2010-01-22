package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.types.PyType;

/**
 * @author yole
 */
public class PyDictCompExpressionImpl extends PyComprehensionElementImpl implements PyDictCompExpression {
  public PyDictCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    return PyBuiltinCache.getInstance(this).getDictType();
  }
}
