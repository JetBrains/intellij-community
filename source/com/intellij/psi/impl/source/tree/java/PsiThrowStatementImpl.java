package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

public class PsiThrowStatementImpl extends CompositePsiElement implements PsiThrowStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiThrowStatementImpl");

  public PsiThrowStatementImpl() {
    super(THROW_STATEMENT);
  }

  public PsiExpression getException() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.EXCEPTION);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.THROW_KEYWORD:
        return TreeUtil.findChild(this, THROW_KEYWORD);

      case ChildRole.EXCEPTION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == THROW_KEYWORD) {
      return ChildRole.THROW_KEYWORD;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.EXCEPTION;
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitThrowStatement(this);
  }

  public String toString() {
    return "PsiThrowStatement";
  }
}
