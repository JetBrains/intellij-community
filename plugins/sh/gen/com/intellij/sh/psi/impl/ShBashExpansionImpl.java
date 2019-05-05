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

public class ShBashExpansionImpl extends ShCompositeElementImpl implements ShBashExpansion {

  public ShBashExpansionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitBashExpansion(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ShBashExpansion> getBashExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShBashExpansion.class);
  }

  @Override
  @NotNull
  public PsiElement getLeftCurly() {
    return findNotNullChildByType(LEFT_CURLY);
  }

  @Override
  @NotNull
  public PsiElement getRightCurly() {
    return findNotNullChildByType(RIGHT_CURLY);
  }

}
