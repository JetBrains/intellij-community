// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.sh.ShTypes.*;
import com.intellij.sh.psi.*;

public class ShAssignmentExpressionImpl extends ShAssignmentExpressionMixin implements ShAssignmentExpression {

  public ShAssignmentExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getAssign() {
    return findChildByType(ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getBitAndAssign() {
    return findChildByType(BIT_AND_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getBitOrAssign() {
    return findChildByType(BIT_OR_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getBitXorAssign() {
    return findChildByType(BIT_XOR_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getDivAssign() {
    return findChildByType(DIV_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getMinusAssign() {
    return findChildByType(MINUS_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getModAssign() {
    return findChildByType(MOD_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getMultAssign() {
    return findChildByType(MULT_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getPlusAssign() {
    return findChildByType(PLUS_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getShiftLeftAssign() {
    return findChildByType(SHIFT_LEFT_ASSIGN);
  }

  @Override
  @Nullable
  public PsiElement getShiftRightAssign() {
    return findChildByType(SHIFT_RIGHT_ASSIGN);
  }

}
