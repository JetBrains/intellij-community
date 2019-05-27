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

public class ShRedirectionImpl extends ShCompositeElementImpl implements ShRedirection {

  public ShRedirectionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitRedirection(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ShArithmeticExpansion> getArithmeticExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShArithmeticExpansion.class);
  }

  @Override
  @NotNull
  public List<ShBraceExpansion> getBraceExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShBraceExpansion.class);
  }

  @Override
  @NotNull
  public List<ShCommand> getCommandList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShCommand.class);
  }

  @Override
  @NotNull
  public List<ShNumber> getNumberList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShNumber.class);
  }

  @Override
  @Nullable
  public ShProcessSubstitution getProcessSubstitution() {
    return findChildByClass(ShProcessSubstitution.class);
  }

  @Override
  @NotNull
  public List<ShShellParameterExpansion> getShellParameterExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShShellParameterExpansion.class);
  }

  @Override
  @NotNull
  public List<ShString> getStringList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShString.class);
  }

  @Override
  @NotNull
  public List<ShVariable> getVariableList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShVariable.class);
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
  public PsiElement getRedirectAmpGreater() {
    return findChildByType(REDIRECT_AMP_GREATER);
  }

  @Override
  @Nullable
  public PsiElement getRedirectAmpGreaterGreater() {
    return findChildByType(REDIRECT_AMP_GREATER_GREATER);
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
