package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

public class PsiInstanceOfExpressionImpl extends CompositePsiElement implements PsiInstanceOfExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiInstanceOfExpressionImpl");

  public PsiInstanceOfExpressionImpl() {
    super(INSTANCE_OF_EXPRESSION);
  }

  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  public PsiTypeElement getCheckType() {
    return (PsiTypeElement)findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public PsiType getType() {
    return PsiType.BOOLEAN;
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.OPERAND:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.INSTANCEOF_KEYWORD:
        return TreeUtil.findChild(this, INSTANCEOF_KEYWORD);

      case ChildRole.TYPE:
        return TreeUtil.findChild(this, TYPE);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == TYPE) {
      return ChildRole.TYPE;
    }
    else if (i == INSTANCEOF_KEYWORD) {
      return ChildRole.INSTANCEOF_KEYWORD;
    }
    else {
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.OPERAND;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitInstanceOfExpression(this);
  }

  public String toString() {
    return "PsiInstanceofExpression:" + getText();
  }
}

