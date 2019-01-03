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

public class BashConditionalCommandImpl extends BashCommandImpl implements BashConditionalCommand {

  public BashConditionalCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitConditionalCommand(this);
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
  public PsiElement getExprConditionalLeft() {
    return findChildByType(EXPR_CONDITIONAL_LEFT);
  }

  @Override
  @Nullable
  public PsiElement getExprConditionalRight() {
    return findChildByType(EXPR_CONDITIONAL_RIGHT);
  }

  @Override
  @Nullable
  public PsiElement getLeftDoubleBracket() {
    return findChildByType(LEFT_DOUBLE_BRACKET);
  }

  @Override
  @Nullable
  public PsiElement getRightDoubleBracket() {
    return findChildByType(RIGHT_DOUBLE_BRACKET);
  }

}
