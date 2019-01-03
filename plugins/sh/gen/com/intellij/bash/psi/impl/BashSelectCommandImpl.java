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

public class BashSelectCommandImpl extends BashCommandImpl implements BashSelectCommand {

  public BashSelectCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitSelectCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<BashArithmeticExpansion> getArithmeticExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashArithmeticExpansion.class);
  }

  @Override
  @NotNull
  public List<BashCommand> getCommandList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashCommand.class);
  }

  @Override
  @Nullable
  public BashListTerminator getListTerminator() {
    return findChildByClass(BashListTerminator.class);
  }

  @Override
  @NotNull
  public List<BashShellParameterExpansion> getShellParameterExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashShellParameterExpansion.class);
  }

  @Override
  @NotNull
  public List<BashString> getStringList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashString.class);
  }

  @Override
  @Nullable
  public PsiElement getSemi() {
    return findChildByType(SEMI);
  }

  @Override
  @NotNull
  public PsiElement getSelect() {
    return findNotNullChildByType(SELECT);
  }

}
