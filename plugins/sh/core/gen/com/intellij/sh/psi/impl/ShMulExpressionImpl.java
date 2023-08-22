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

public class ShMulExpressionImpl extends ShBinaryExpressionImpl implements ShMulExpression {

  public ShMulExpressionImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitMulExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getDiv() {
    return findChildByType(DIV);
  }

  @Override
  @Nullable
  public PsiElement getMod() {
    return findChildByType(MOD);
  }

  @Override
  @Nullable
  public PsiElement getMult() {
    return findChildByType(MULT);
  }

}
