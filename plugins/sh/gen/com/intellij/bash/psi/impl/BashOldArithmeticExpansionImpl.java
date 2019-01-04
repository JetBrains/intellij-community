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

public class BashOldArithmeticExpansionImpl extends BashArithmeticExpansionImpl implements BashOldArithmeticExpansion {

  public BashOldArithmeticExpansionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitOldArithmeticExpansion(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getArithSquareLeft() {
    return findNotNullChildByType(ARITH_SQUARE_LEFT);
  }

  @Override
  @Nullable
  public PsiElement getArithSquareRight() {
    return findChildByType(ARITH_SQUARE_RIGHT);
  }

}
