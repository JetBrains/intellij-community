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

public class BashIfCommandImpl extends BashCommandImpl implements BashIfCommand {

  public BashIfCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitIfCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashCompoundList getCompoundList() {
    return findChildByClass(BashCompoundList.class);
  }

  @Override
  @NotNull
  public List<BashElifClause> getElifClauseList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashElifClause.class);
  }

  @Override
  @Nullable
  public BashElseClause getElseClause() {
    return findChildByClass(BashElseClause.class);
  }

  @Override
  @Nullable
  public BashThenClause getThenClause() {
    return findChildByClass(BashThenClause.class);
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
