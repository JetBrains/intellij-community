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

public class BashPostExpressionImpl extends BashExpressionImpl implements BashPostExpression {

  public BashPostExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitPostExpression(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public BashExpression getExpression() {
    return findNotNullChildByClass(BashExpression.class);
  }

  @Override
  @Nullable
  public PsiElement getArithMinusMinus() {
    return findChildByType(ARITH_MINUS_MINUS);
  }

  @Override
  @Nullable
  public PsiElement getArithPlusPlus() {
    return findChildByType(ARITH_PLUS_PLUS);
  }

}
