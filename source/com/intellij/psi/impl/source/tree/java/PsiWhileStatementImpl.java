package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

public class PsiWhileStatementImpl extends CompositePsiElement implements PsiWhileStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiWhileStatementImpl");

  public PsiWhileStatementImpl() {
    super(WHILE_STATEMENT);
  }

  public PsiExpression getCondition(){
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  public PsiStatement getBody(){
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.LOOP_BODY);
  }

  public PsiJavaToken getLParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.LPARENTH);
  }

  public PsiJavaToken getRParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.RPARENTH);
  }

  public TreeElement findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.WHILE_KEYWORD:
        return TreeUtil.findChild(this, WHILE_KEYWORD);

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.CONDITION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.LOOP_BODY:
        return TreeUtil.findChild(this, STATEMENT_BIT_SET);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == WHILE_KEYWORD) {
      return ChildRole.WHILE_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.CONDITION;
      }
      else if (STATEMENT_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.LOOP_BODY;
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitWhileStatement(this);
  }

  public String toString(){
    return "PsiWhileStatement";
  }
}
