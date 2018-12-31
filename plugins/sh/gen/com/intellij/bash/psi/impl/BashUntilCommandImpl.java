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

public class BashUntilCommandImpl extends BashCommandImpl implements BashUntilCommand {

  public BashUntilCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitUntilCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<BashCompoundList> getCompoundListList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashCompoundList.class);
  }

  @Override
  @Nullable
  public PsiElement getDo() {
    return findChildByType(DO);
  }

  @Override
  @Nullable
  public PsiElement getDone() {
    return findChildByType(DONE);
  }

  @Override
  @NotNull
  public PsiElement getUntil() {
    return findNotNullChildByType(UNTIL);
  }

}
