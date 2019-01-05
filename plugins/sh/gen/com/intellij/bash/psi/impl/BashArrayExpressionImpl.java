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

public class BashArrayExpressionImpl extends BashExpressionImpl implements BashArrayExpression {

  public BashArrayExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitArrayExpression(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashExpression getExpression() {
    return findChildByClass(BashExpression.class);
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
