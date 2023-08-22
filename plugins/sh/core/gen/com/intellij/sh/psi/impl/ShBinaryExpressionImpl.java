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

public class ShBinaryExpressionImpl extends ShExpressionImpl implements ShBinaryExpression {

  public ShBinaryExpressionImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitBinaryExpression(this);
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
  public ShExpression getLeft() {
    List<ShExpression> p1 = getExpressionList();
    return p1.get(0);
  }

  @Override
  @Nullable
  public ShExpression getRight() {
    List<ShExpression> p1 = getExpressionList();
    return p1.size() < 2 ? null : p1.get(1);
  }

}
