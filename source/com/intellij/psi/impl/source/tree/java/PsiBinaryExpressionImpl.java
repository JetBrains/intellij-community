package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;

public class PsiBinaryExpressionImpl extends CompositePsiElement implements PsiBinaryExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl");

  public PsiBinaryExpressionImpl() {
    super(BINARY_EXPRESSION);
  }

  public PsiExpression getLOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.LOPERAND);
  }

  public PsiExpression getROperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ROPERAND);
  }

  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  public PsiType getType() {
    PsiExpression lOperand = getLOperand();
    PsiExpression rOperand = getROperand();
    if (rOperand == null) return null;
    PsiType type1 = lOperand.getType();
    PsiType type2 = rOperand.getType();

    IElementType i = (SourceTreeToPsiMap.psiElementToTree(getOperationSign())).getElementType();
    if (i == PLUS) {
      if (type1 == null || type2 == null) return null;
      if (type1.equalsToText("java.lang.String") || type2.equalsToText("java.lang.String")) {
        return PsiType.getJavaLangString(getManager(), getResolveScope());
      }

      if (type1 == null && type2 == null) return null;
      if (type1 == PsiType.DOUBLE || type2 == PsiType.DOUBLE) return PsiType.DOUBLE;
      if (type1 == PsiType.FLOAT || type2 == PsiType.FLOAT) return PsiType.FLOAT;
      if (type1 == PsiType.LONG || type2 == PsiType.LONG) return PsiType.LONG;
      return PsiType.INT;
    }
    else if (i == MINUS || i == ASTERISK || i == DIV || i == PERC) {
      if (type1 == null && type2 == null) return null;
      if (type1 == PsiType.DOUBLE || type2 == PsiType.DOUBLE) return PsiType.DOUBLE;
      if (type1 == PsiType.FLOAT || type2 == PsiType.FLOAT) return PsiType.FLOAT;
      if (type1 == PsiType.LONG || type2 == PsiType.LONG) return PsiType.LONG;
      return PsiType.INT;
    }
    else if (i == LTLT || i == GTGT || i == GTGTGT) {
      if (type1 == PsiType.BYTE || type1 == PsiType.CHAR || type1 == PsiType.SHORT) {
        return PsiType.INT;
      }
      else {
        return type1;
      }
    }
    else if (i == EQEQ || i == NE || i == LT || i == GT || i == LE || i == GE || i == OROR || i == ANDAND) {
      return PsiType.BOOLEAN;
    }
    else if (i == OR || i == XOR || i == AND) {
      if (type1 == null && type2 == null) return null;
      if (type1 == PsiType.BOOLEAN || type2 == PsiType.BOOLEAN) return PsiType.BOOLEAN;
      if (type1 == PsiType.LONG || type2 == PsiType.LONG) return PsiType.LONG;
      return PsiType.INT;
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
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
    OROR, ANDAND, OR, XOR,
    AND, EQEQ, NE, LT,
    GT, LE, GE, LTLT,
    GTGT, GTGTGT, PLUS, MINUS,
    ASTERISK, DIV, PERC
  });

  public void accept(PsiElementVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }

  public String toString() {
    return "PsiBinaryExpression:" + getText();
  }
}

