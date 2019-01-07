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
  @Nullable
  public BashArithmeticExpansion getArithmeticExpansion() {
    return findChildByClass(BashArithmeticExpansion.class);
  }

  @Override
  @Nullable
  public BashBashExpansion getBashExpansion() {
    return findChildByClass(BashBashExpansion.class);
  }

  @Override
  @Nullable
  public BashCommand getCommand() {
    return findChildByClass(BashCommand.class);
  }

  @Override
  @Nullable
  public BashShellParameterExpansion getShellParameterExpansion() {
    return findChildByClass(BashShellParameterExpansion.class);
  }

  @Override
  @Nullable
  public BashString getString() {
    return findChildByClass(BashString.class);
  }

  @Override
  @Nullable
  public BashVariable getVariable() {
    return findChildByClass(BashVariable.class);
  }

  @Override
  @Nullable
  public PsiElement getArithMinus() {
    return findChildByType(ARITH_MINUS);
  }

  @Override
  @Nullable
  public PsiElement getAt() {
    return findChildByType(AT);
  }

  @Override
  @Nullable
  public PsiElement getBang() {
    return findChildByType(BANG);
  }

  @Override
  @Nullable
  public PsiElement getDollar() {
    return findChildByType(DOLLAR);
  }

  @Override
  @Nullable
  public PsiElement getFiledescriptor() {
    return findChildByType(FILEDESCRIPTOR);
  }

  @Override
  @Nullable
  public PsiElement getGreaterThan() {
    return findChildByType(GREATER_THAN);
  }

  @Override
  @Nullable
  public PsiElement getLessThan() {
    return findChildByType(LESS_THAN);
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

  @Override
  @Nullable
  public PsiElement getHex() {
    return findChildByType(HEX);
  }

  @Override
  @Nullable
  public PsiElement getInt() {
    return findChildByType(INT);
  }

  @Override
  @Nullable
  public PsiElement getNumber() {
    return findChildByType(NUMBER);
  }

  @Override
  @Nullable
  public PsiElement getOctal() {
    return findChildByType(OCTAL);
  }

  @Override
  @Nullable
  public PsiElement getWord() {
    return findChildByType(WORD);
  }

}
