package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

public class PsiReturnStatementImpl extends CompositePsiElement implements PsiReturnStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiReturnStatementImpl");

  public PsiReturnStatementImpl() {
    super(RETURN_STATEMENT);
  }

  public PsiExpression getReturnValue() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.RETURN_VALUE);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.RETURN_KEYWORD:
        return TreeUtil.findChild(this, RETURN_KEYWORD);

      case ChildRole.RETURN_VALUE:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == RETURN_KEYWORD) {
      return ChildRole.RETURN_KEYWORD;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.RETURN_VALUE;
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReturnStatement(this);
  }

  public String toString() {
    return "PsiReturnStatement";
  }
}
