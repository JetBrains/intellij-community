package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class PsiBinaryExpressionImpl extends CompositePsiElement implements PsiBinaryExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl");

  public PsiBinaryExpressionImpl() {
    super(BINARY_EXPRESSION);
  }

  @NotNull
  public PsiExpression getLOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.LOPERAND);
  }

  public PsiExpression getROperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ROPERAND);
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
    PsiExpression lOperand = getLOperand();
    PsiExpression rOperand = getROperand();
    if (rOperand == null) return null;

    IElementType i = SourceTreeToPsiMap.psiElementToTree(getOperationSign()).getElementType();
    if (i == PLUS) {
      // evaluate right argument first, since '+-/*%' is left associative and left operand tends to be bigger
      PsiType type2 = rOperand.getType();
      if (type2 == null) return null;
      if (type2.equalsToText("java.lang.String")) {
        return type2;
      }
      PsiType type1 = lOperand.getType();
      if (type1 == null) return null;
      if (type1.equalsToText("java.lang.String")) {
        return type1;
      }
      return unboxAndBalanceTypes(type1, type2);
    }
    if (i == MINUS || i == ASTERISK || i == DIV || i == PERC) {
      PsiType type2 = rOperand.getType();
      PsiType type1 = lOperand.getType();
      if (type1 == null && type2 == null) return null;
      return unboxAndBalanceTypes(type1, type2);
    }
    if (i == LTLT || i == GTGT || i == GTGTGT) {
      PsiType type1 = lOperand.getType();
      if (type1 == PsiType.BYTE || type1 == PsiType.CHAR || type1 == PsiType.SHORT) {
        return PsiType.INT;
      }
      return type1;
    }
    if (i == EQEQ || i == NE || i == LT || i == GT || i == LE || i == GE || i == OROR || i == ANDAND) {
      return PsiType.BOOLEAN;
    }
    if (i == OR || i == XOR || i == AND) {
      PsiType type2 = rOperand.getType();
      PsiType type1 = lOperand.getType();
      if (type1 instanceof PsiClassType) type1 = PsiPrimitiveType.getUnboxedType(type1);
      if (type2 instanceof PsiClassType) type2 = PsiPrimitiveType.getUnboxedType(type2);

      if (type1 == null && type2 == null) return null;
      if (type1 == PsiType.BOOLEAN || type2 == PsiType.BOOLEAN) return PsiType.BOOLEAN;
      if (type1 == PsiType.LONG || type2 == PsiType.LONG) return PsiType.LONG;
      return PsiType.INT;
    }

    LOG.assertTrue(false);
    return null;
  }

  private static PsiType unboxAndBalanceTypes(PsiType type1, PsiType type2) {
    if (type1 instanceof PsiClassType) type1 = PsiPrimitiveType.getUnboxedType(type1);
    if (type2 instanceof PsiClassType) type2 = PsiPrimitiveType.getUnboxedType(type2);

    if (type1 == PsiType.DOUBLE || type2 == PsiType.DOUBLE) return PsiType.DOUBLE;
    if (type1 == PsiType.FLOAT || type2 == PsiType.FLOAT) return PsiType.FLOAT;
    if (type1 == PsiType.LONG || type2 == PsiType.LONG) return PsiType.LONG;
    return PsiType.INT;
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LOPERAND:
        return getFirstChildNode();

      case ChildRole.ROPERAND:
        return EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;

      case ChildRole.OPERATION_SIGN:
        return TreeUtil.findChild(this, OUR_OPERATIONS_BIT_SET);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
      if (child == getFirstChildNode()) return ChildRole.LOPERAND;
      if (child == getLastChildNode()) return ChildRole.ROPERAND;
      return ChildRole.NONE;
    }
    else if (OUR_OPERATIONS_BIT_SET.contains(child.getElementType())) {
      return ChildRole.OPERATION_SIGN;
    }
    else {
      return ChildRole.NONE;
    }
  }

  private static final TokenSet OUR_OPERATIONS_BIT_SET =
    TokenSet.create(OROR, ANDAND, OR, XOR, AND, EQEQ, NE, LT, GT, LE, GE, LTLT, GTGT, GTGTGT, PLUS, MINUS, ASTERISK, DIV, PERC);

  public void accept(PsiElementVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }

  public String toString() {
    return "PsiBinaryExpression:" + getText();
  }
}

