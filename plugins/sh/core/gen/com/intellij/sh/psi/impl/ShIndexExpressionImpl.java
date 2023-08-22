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

public class ShIndexExpressionImpl extends ShExpressionImpl implements ShIndexExpression {

  public ShIndexExpressionImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitIndexExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ShExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShExpression.class);
  }

  @Override
  @NotNull
  public PsiElement getLeftSquare() {
    return findNotNullChildByType(LEFT_SQUARE);
  }

  @Override
  @Nullable
  public PsiElement getRightSquare() {
    return findChildByType(RIGHT_SQUARE);
  }

}
