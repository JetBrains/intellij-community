package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;

public class PsiPrefixExpressionImpl extends CompositePsiElement implements PsiPrefixExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl");

  public PsiPrefixExpressionImpl() {
    super(PREFIX_EXPRESSION);
  }

  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  public PsiType getType() {
    PsiElementFactoryImpl factory = (PsiElementFactoryImpl)getManager().getElementFactory();
    PsiExpression operand = getOperand();
    if (operand == null) return null;
    PsiType type = operand.getType();
    IElementType opCode = (SourceTreeToPsiMap.psiElementToTree(getOperationSign())).getElementType();
    if (opCode == PLUS || opCode == MINUS || opCode == TILDE) {
      if (type == null) return null;
      if (type == PsiType.BYTE || type == PsiType.CHAR || type == PsiType.SHORT) {
        return PsiType.INT;
      }
      else {
        return type;
      }
    }
    else if (opCode == PLUSPLUS || opCode == MINUSMINUS) {
      return type;
    }
    else if (opCode == EXCL) {
      return PsiType.BOOLEAN;
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.OPERATION_SIGN:
        return firstChild;

      case ChildRole.OPERAND:
        return EXPRESSION_BIT_SET.isInSet(lastChild.getElementType()) ? lastChild : null;
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child == firstChild) return ChildRole.OPERATION_SIGN;
    if (child == lastChild && EXPRESSION_BIT_SET.isInSet(child.getElementType())) return ChildRole.OPERAND;
    return ChildRole.NONE;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitPrefixExpression(this);
  }

  public String toString() {
    return "PsiPrefixExpression:" + getText();
  }
}

