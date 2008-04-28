package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiBinaryExpressionImpl extends ExpressionPsiElement implements PsiBinaryExpression, Constants {
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

    return getType(lOperand.getType(), rOperand.getType(), SourceTreeToPsiMap.psiElementToTree(getOperationSign()).getElementType());
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
      return ChildRoleBase.NONE;
    }
    else if (OUR_OPERATIONS_BIT_SET.contains(child.getElementType())) {
      return ChildRole.OPERATION_SIGN;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  private static final TokenSet OUR_OPERATIONS_BIT_SET =
    TokenSet.create(OROR, ANDAND, OR, XOR, AND, EQEQ, NE, LT, GT, LE, GE, LTLT, GTGT, GTGTGT, PLUS, MINUS, ASTERISK, DIV, PERC);

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitBinaryExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiBinaryExpression:" + getText();
  }

  @Nullable
  public static PsiType getType(PsiType lType, PsiType rType, IElementType sign) {
    if (sign == PLUS) {
      // evaluate right argument first, since '+-/*%' is left associative and left operand tends to be bigger
      if (rType == null) return null;
      if (rType.equalsToText("java.lang.String")) {
        return rType;
      }
      if (lType == null) return null;
      if (lType.equalsToText("java.lang.String")) {
        return lType;
      }
      return TypeConversionUtil.unboxAndBalanceTypes(lType, rType);
    }
    if (sign == MINUS || sign == ASTERISK || sign == DIV || sign == PERC) {
      if (lType == null && rType == null) return null;
      return TypeConversionUtil.unboxAndBalanceTypes(lType, rType);
    }
    if (sign == LTLT || sign == GTGT || sign == GTGTGT) {
      if (lType == PsiType.BYTE || lType == PsiType.CHAR || lType == PsiType.SHORT) {
        return PsiType.INT;
      }
      return lType;
    }
    if (sign == EQEQ || sign == NE || sign == LT || sign == GT || sign == LE || sign == GE || sign == OROR || sign == ANDAND) {
      return PsiType.BOOLEAN;
    }
    if (sign == OR || sign == XOR || sign == AND) {
      if (lType instanceof PsiClassType) lType = PsiPrimitiveType.getUnboxedType(lType);
      if (rType instanceof PsiClassType) rType = PsiPrimitiveType.getUnboxedType(rType);

      if (lType == null && rType == null) return null;
      if (lType == PsiType.BOOLEAN || rType == PsiType.BOOLEAN) return PsiType.BOOLEAN;
      if (lType == PsiType.LONG || rType == PsiType.LONG) return PsiType.LONG;
      return PsiType.INT;
    }
    LOG.assertTrue(false);
    return null;
  }
}

