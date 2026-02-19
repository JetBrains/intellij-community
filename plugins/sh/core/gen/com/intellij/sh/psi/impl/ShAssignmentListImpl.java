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

public class ShAssignmentListImpl extends ShCompositeElementImpl implements ShAssignmentList {

  public ShAssignmentListImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitAssignmentList(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ShArrayAssignment> getArrayAssignmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShArrayAssignment.class);
  }

  @Override
  @NotNull
  public PsiElement getLeftParen() {
    return findNotNullChildByType(LEFT_PAREN);
  }

  @Override
  @Nullable
  public PsiElement getRightParen() {
    return findChildByType(RIGHT_PAREN);
  }

}
