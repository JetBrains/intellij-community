package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
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

  @Override
  public PyExpression getExpression() {
    ASTNode[] children = getNode().getChildren(null);
    for (ASTNode child: children) {
      final PsiElement e = child.getPsi();
      if (e instanceof PyExpression) {
        return (PyExpression)e;
      }
      else if (child.getElementType() == PyTokenTypes.AS_KEYWORD) {
        break;
      }
    }
    return null;
  }

  @Override
  public PyExpression getTarget() {
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
