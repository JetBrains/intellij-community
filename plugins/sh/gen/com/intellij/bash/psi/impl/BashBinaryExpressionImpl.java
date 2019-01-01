// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.bash.BashTypes.*;
import com.intellij.bash.psi.*;

public class BashBinaryExpressionImpl extends BashExpressionImpl implements BashBinaryExpression {

  public BashBinaryExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<BashExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashExpression.class);
  }

  @Override
  @NotNull
  public BashExpression getLeft() {
    List<BashExpression> p1 = getExpressionList();
    return p1.get(0);
  }

  @Override
  @Nullable
  public BashExpression getRight() {
    List<BashExpression> p1 = getExpressionList();
    return p1.size() < 2 ? null : p1.get(1);
  }

}
