package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.source.tree.*;

public class PsiAssignmentExpressionImpl extends CompositePsiElement implements PsiAssignmentExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl");

  public PsiAssignmentExpressionImpl() {
    super(ASSIGNMENT_EXPRESSION);
  }

  public PsiExpression getLExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.LOPERAND);
  }

  public PsiExpression getRExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ROPERAND);
  }

  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  public PsiType getType() {
    return getLExpression().getType();
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LOPERAND:
        return firstChild;

      case ChildRole.ROPERAND:
        return EXPRESSION_BIT_SET.isInSet(lastChild.getElementType()) ? lastChild : null;

      case ChildRole.OPERATION_SIGN:
        return TreeUtil.findChild(this, OUR_OPERATIONS_BIT_SET);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
      if (child == firstChild) return ChildRole.LOPERAND;
      if (child == lastChild) return ChildRole.ROPERAND;
      return ChildRole.NONE;
    }
    else if (OUR_OPERATIONS_BIT_SET.isInSet(child.getElementType())) {
      return ChildRole.OPERATION_SIGN;
    }
    else {
      return ChildRole.NONE;
    }
  }

  private static final TokenSet OUR_OPERATIONS_BIT_SET = TokenSet.create(new IElementType[]{
    EQ, ASTERISKEQ, DIVEQ, PERCEQ,
    PLUSEQ, MINUSEQ, LTLTEQ, GTGTEQ,
    GTGTGTEQ, ANDEQ, OREQ, XOREQ
  });

  public void accept(PsiElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  public String toString() {
    return "PsiAssignmentExpression:" + getText();
  }
}

