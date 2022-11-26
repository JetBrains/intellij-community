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

public class ShIfCommandImpl extends ShCommandImpl implements ShIfCommand {

  public ShIfCommandImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitIfCommand(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ShCompoundList getCompoundList() {
    return findChildByClass(ShCompoundList.class);
  }

  @Override
  @NotNull
  public List<ShElifClause> getElifClauseList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShElifClause.class);
  }

  @Override
  @Nullable
  public ShElseClause getElseClause() {
    return findChildByClass(ShElseClause.class);
  }

  @Override
  @Nullable
  public ShThenClause getThenClause() {
    return findChildByClass(ShThenClause.class);
  }

  @Override
  @Nullable
  public PsiElement getFi() {
    return findChildByType(FI);
  }

  @Override
  @NotNull
  public PsiElement getIf() {
    return findNotNullChildByType(IF);
  }

}
