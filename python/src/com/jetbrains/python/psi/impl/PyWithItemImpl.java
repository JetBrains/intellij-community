package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithItem;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyWithItemImpl extends PyElementImpl implements PyWithItem {
  public PyWithItemImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  public PyExpression getTargetExpression() {
    ASTNode[] children = getNode().getChildren(null);
    boolean foundAs = false;
    for (ASTNode child : children) {
      if (child.getElementType() == PyTokenTypes.AS_KEYWORD) {
        foundAs = true;
      }
      else if (foundAs && child.getPsi() instanceof PyExpression) {
        return (PyExpression) child.getPsi();
      }
    }
    return null;
  }
}
