package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyWithItem;

/**
 * @author yole
 */
public class PyWithItemImpl extends PyElementImpl implements PyWithItem {
  public PyWithItemImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTargetExpression getTargetExpression() {
    final ASTNode asNameNode = getNode().findChildByType(PyElementTypes.TARGET_EXPRESSION);
    if (asNameNode == null) return null;
    return (PyTargetExpression)asNameNode.getPsi();
  }
}
