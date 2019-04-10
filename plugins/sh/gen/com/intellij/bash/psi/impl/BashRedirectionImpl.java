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

public class BashRedirectionImpl extends BashCompositeElementImpl implements BashRedirection {

  public BashRedirectionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitRedirection(this);
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
  public List<BashBashExpansion> getBashExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashBashExpansion.class);
  }

  @Override
  @NotNull
  public List<BashCommand> getCommandList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashCommand.class);
  }

  @Override
  @Nullable
  public BashProcessSubstitution getProcessSubstitution() {
    return findChildByClass(BashProcessSubstitution.class);
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
  public List<BashVariable> getVariableList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashVariable.class);
  }

  @Override
  @Nullable
  public PsiElement getGt() {
    return findChildByType(GT);
  }

  @Override
  @Nullable
  public PsiElement getLt() {
    return findChildByType(LT);
  }

  @Override
  @Nullable
  public PsiElement getMinus() {
    return findChildByType(MINUS);
  }

  @Override
  @Nullable
  public PsiElement getRedirectGreaterAmp() {
    return findChildByType(REDIRECT_GREATER_AMP);
  }

  @Override
  @Nullable
  public PsiElement getRedirectGreaterBar() {
    return findChildByType(REDIRECT_GREATER_BAR);
  }

  @Override
  @Nullable
  public PsiElement getRedirectHereString() {
    return findChildByType(REDIRECT_HERE_STRING);
  }

  @Override
  @Nullable
  public PsiElement getRedirectLessAmp() {
    return findChildByType(REDIRECT_LESS_AMP);
  }

  @Override
  @Nullable
  public PsiElement getRedirectLessGreater() {
    return findChildByType(REDIRECT_LESS_GREATER);
  }

  @Override
  @Nullable
  public PsiElement getShiftLeft() {
    return findChildByType(SHIFT_LEFT);
  }

  @Override
  @Nullable
  public PsiElement getShiftRight() {
    return findChildByType(SHIFT_RIGHT);
  }

}
