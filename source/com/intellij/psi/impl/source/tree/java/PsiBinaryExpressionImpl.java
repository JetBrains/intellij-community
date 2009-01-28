package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiBinaryExpressionImpl extends ExpressionPsiElement implements PsiBinaryExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl");

  public PsiBinaryExpressionImpl() {
    super(JavaElementType.BINARY_EXPRESSION);
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

  private static final Key<ParameterizedCachedValue<PsiType, PsiBinaryExpressionImpl>> TYPE_KEY = Key.create("TYPE_KEY");
  private static final ParameterizedCachedValueProvider<PsiType,PsiBinaryExpressionImpl> TYPE_PROVIDER = new ParameterizedCachedValueProvider<PsiType, PsiBinaryExpressionImpl>() {
    public CachedValueProvider.Result<PsiType> compute(PsiBinaryExpressionImpl param) {
      PsiExpression lOperand = param.getLOperand();
      PsiExpression rOperand = param.getROperand();
      if (rOperand == null) return null;

      PsiType rType = rOperand.getType();
      PsiType lType = lOperand.getType();
      PsiType result = getType(lType, rType, SourceTreeToPsiMap.psiElementToTree(param.getOperationSign()).getElementType());

      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    }
  };
  public PsiType getType() {
    return getManager().getCachedValuesManager().getParameterizedCachedValue(this, TYPE_KEY, TYPE_PROVIDER, false, this);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LOPERAND:
        return getFirstChildNode();

      case ChildRole.ROPERAND:
        return ElementType.EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;

      case ChildRole.OPERATION_SIGN:
        return TreeUtil.findChild(this, OUR_OPERATIONS_BIT_SET);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
      if (child == getFirstChildNode()) return ChildRole.LOPERAND;
      if (child == getLastChildNode()) return ChildRole.ROPERAND;
      return ChildRoleBase.NONE;
    }
    if (OUR_OPERATIONS_BIT_SET.contains(child.getElementType())) {
      return ChildRole.OPERATION_SIGN;
    }
    return ChildRoleBase.NONE;
  }

  private static final TokenSet OUR_OPERATIONS_BIT_SET =
    TokenSet.create(JavaTokenType.OROR, JavaTokenType.ANDAND, JavaTokenType.OR, JavaTokenType.XOR, JavaTokenType.AND, JavaTokenType.EQEQ,
                    JavaTokenType.NE, JavaTokenType.LT, JavaTokenType.GT, JavaTokenType.LE, JavaTokenType.GE, JavaTokenType.LTLT,
                    JavaTokenType.GTGT, JavaTokenType.GTGTGT, JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.ASTERISK, JavaTokenType.DIV,
                    JavaTokenType.PERC);

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
    if (sign == JavaTokenType.PLUS) {
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
    if (sign == JavaTokenType.MINUS || sign == JavaTokenType.ASTERISK || sign == JavaTokenType.DIV || sign == JavaTokenType.PERC) {
      if (lType == null && rType == null) return null;
      return TypeConversionUtil.unboxAndBalanceTypes(lType, rType);
    }
    if (sign == JavaTokenType.LTLT || sign == JavaTokenType.GTGT || sign == JavaTokenType.GTGTGT) {
      if (lType == PsiType.BYTE || lType == PsiType.CHAR || lType == PsiType.SHORT) {
        return PsiType.INT;
      }
      return lType;
    }
    if (sign == JavaTokenType.EQEQ ||
        sign == JavaTokenType.NE ||
        sign == JavaTokenType.LT ||
        sign == JavaTokenType.GT ||
        sign == JavaTokenType.LE ||
        sign == JavaTokenType.GE ||
        sign == JavaTokenType.OROR ||
        sign == JavaTokenType.ANDAND) {
      return PsiType.BOOLEAN;
    }
    if (sign == JavaTokenType.OR || sign == JavaTokenType.XOR || sign == JavaTokenType.AND) {
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

