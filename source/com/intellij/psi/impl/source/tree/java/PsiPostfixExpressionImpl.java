package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PsiPostfixExpressionImpl extends CompositePsiElement implements PsiPostfixExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiPostfixExpressionImpl");

  public PsiPostfixExpressionImpl() {
    super(POSTFIX_EXPRESSION);
  }

  @NotNull
  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  @NotNull
  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  @NotNull
  public IElementType getOperationTokenType() {
    return getOperationSign().getTokenType();
  }

  public PsiType getType() {
    return getOperand().getType();
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.OPERAND:
        return getFirstChildNode();

      case ChildRole.OPERATION_SIGN:
        return getLastChildNode();
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child == getFirstChildNode()) return ChildRole.OPERAND;
    if (child == getLastChildNode()) return ChildRole.OPERATION_SIGN;
    return ChildRole.NONE;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitPostfixExpression(this);
  }

  public String toString() {
    return "PsiPostfixExpression:" + getText();
  }
}

