package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

public class PsiConditionalExpressionImpl extends ExpressionPsiElement implements PsiConditionalExpression, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiConditionalExpressionImpl");

  public PsiConditionalExpressionImpl() {
    super(CONDITIONAL_EXPRESSION);
  }

  @NotNull
  public PsiExpression getCondition() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  public PsiExpression getThenExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.THEN_EXPRESSION);
  }

  public PsiExpression getElseExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ELSE_EXPRESSION);
  }

  /**
   * JLS 15.25
   */
  public PsiType getType() {
    PsiExpression expr1 = getThenExpression();
    PsiExpression expr2 = getElseExpression();
    PsiType type1 = expr1 != null ? expr1.getType() : null;
    PsiType type2 = expr2 != null ? expr2.getType() : null;
    if (type1 == null) return type2;
    if (type2 == null) return type1;

    if (type1.equals(type2)) return type1;
    final int typeRank1 = TypeConversionUtil.getTypeRank(type1);
    final int typeRank2 = TypeConversionUtil.getTypeRank(type2);
    if (TypeConversionUtil.isNumericType(typeRank1) && TypeConversionUtil.isNumericType(typeRank2)){
      if (typeRank1 == TypeConversionUtil.BYTE_RANK && typeRank2 == TypeConversionUtil.SHORT_RANK) return type2;
      if (typeRank1 == TypeConversionUtil.SHORT_RANK && typeRank2 == TypeConversionUtil.BYTE_RANK) return type1;
      if (typeRank1 == TypeConversionUtil.BYTE_RANK || typeRank1 == TypeConversionUtil.SHORT_RANK || typeRank1 == TypeConversionUtil.CHAR_RANK){
        if (TypeConversionUtil.areTypesAssignmentCompatible(type1, expr2)) return type1;
      }
      if (typeRank2 == TypeConversionUtil.BYTE_RANK || typeRank2 == TypeConversionUtil.SHORT_RANK || typeRank2 == TypeConversionUtil.CHAR_RANK){
        if (TypeConversionUtil.areTypesAssignmentCompatible(type2, expr1)) return type2;
      }
      return TypeConversionUtil.binaryNumericPromotion(type1, type2);
    }
    if (TypeConversionUtil.isNullType(type1) && !(type2 instanceof PsiPrimitiveType)) return type2;
    if (TypeConversionUtil.isNullType(type2) && !(type1 instanceof PsiPrimitiveType)) return type1;

    if (TypeConversionUtil.isAssignable(type1, type2, false)) return type1;
    if (TypeConversionUtil.isAssignable(type2, type1, false)) return type2;
    if (!PsiUtil.isLanguageLevel5OrHigher(this)) {
      return null;
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(type1)) type1 = ((PsiPrimitiveType)type1).getBoxedType(this);
    if (type1 == null) return null;
    if (TypeConversionUtil.isPrimitiveAndNotNull(type2)) type2 = ((PsiPrimitiveType)type2).getBoxedType(this);
    if (type2 == null) return null;

    return GenericsUtil.getLeastUpperBound(type1, type2, getManager());
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.CONDITION:
        return getFirstChildNode();

      case ChildRole.QUEST:
        return TreeUtil.findChild(this, QUEST);

      case ChildRole.THEN_EXPRESSION:
        {
          ASTNode quest = findChildByRole(ChildRole.QUEST);
          ASTNode child = quest.getTreeNext();
          while(true){
            if (child == null) return null;
            if (EXPRESSION_BIT_SET.contains(child.getElementType())) break;
            child = child.getTreeNext();
          }
          return child;
        }

      case ChildRole.COLON:
        return TreeUtil.findChild(this, COLON);

      case ChildRole.ELSE_EXPRESSION:
        {
          ASTNode colon = findChildByRole(ChildRole.COLON);
          if (colon == null) return null;
          return EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;
        }
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (EXPRESSION_BIT_SET.contains(child.getElementType())){
      int role = getChildRole(child, ChildRole.CONDITION);
      if (role != ChildRoleBase.NONE) return role;
      role = getChildRole(child, ChildRole.THEN_EXPRESSION);
      if (role != ChildRoleBase.NONE) return role;
      role = getChildRole(child, ChildRole.ELSE_EXPRESSION);
      return role;
    }
    else if (child.getElementType() == QUEST){
      return ChildRole.QUEST;
    }
    else if (child.getElementType() == COLON){
      return ChildRole.COLON;
    }
    else{
      return ChildRoleBase.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitConditionalExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiConditionalExpression:" + getText();
  }
}

