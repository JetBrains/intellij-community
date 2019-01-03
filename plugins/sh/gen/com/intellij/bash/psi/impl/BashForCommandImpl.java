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

public class BashForCommandImpl extends BashCommandImpl implements BashForCommand {

  public BashForCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitForCommand(this);
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
  @Nullable
  public BashBlock getBlock() {
    return findChildByClass(BashBlock.class);
  }

  @Override
  @NotNull
  public List<BashCommand> getCommandList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashCommand.class);
  }

  @Override
  @NotNull
  public List<BashExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashExpression.class);
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
  @NotNull
  public PsiElement getFor() {
    return findNotNullChildByType(FOR);
  }

}
