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

public class ShLogicalOrConditionImpl extends ShConditionImpl implements ShLogicalOrCondition {

  public ShLogicalOrConditionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitLogicalOrCondition(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ShCondition> getConditionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShCondition.class);
  }

  @Override
  @Nullable
  public PsiElement getOrOr() {
    return findChildByType(OR_OR);
  }

}
