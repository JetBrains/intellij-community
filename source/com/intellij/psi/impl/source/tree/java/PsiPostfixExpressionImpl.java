package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;

public class PsiPostfixExpressionImpl extends CompositePsiElement implements PsiPostfixExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiPostfixExpressionImpl");

  public PsiPostfixExpressionImpl() {
    super(POSTFIX_EXPRESSION);
  }

  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  public PsiType getType() {
    return getOperand().getType();
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.OPERAND:
        return firstChild;

      case ChildRole.OPERATION_SIGN:
        return lastChild;
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child == firstChild) return ChildRole.OPERAND;
    if (child == lastChild) return ChildRole.OPERATION_SIGN;
    return ChildRole.NONE;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitPostfixExpression(this);
  }

  public String toString() {
    return "PsiPostfixExpression:" + getText();
  }
}

