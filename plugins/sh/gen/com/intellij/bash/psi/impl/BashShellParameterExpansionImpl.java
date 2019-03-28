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

public class BashShellParameterExpansionImpl extends BashCompositeElementImpl implements BashShellParameterExpansion {

  public BashShellParameterExpansionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitShellParameterExpansion(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getLeftCurly() {
    return findNotNullChildByType(LEFT_CURLY);
  }

  @Override
  @Nullable
  public PsiElement getRightCurly() {
    return findChildByType(RIGHT_CURLY);
  }

  @Override
  @Nullable
  public PsiElement getParameterExpansionBody() {
    return findChildByType(PARAMETER_EXPANSION_BODY);
  }

}
