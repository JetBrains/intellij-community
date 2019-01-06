// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashRedirection extends BashCompositeElement {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashCommand getCommand();

  @Nullable
  BashShellParameterExpansion getShellParameterExpansion();

  @Nullable
  BashString getString();

  @Nullable
  BashVariable getVariable();

  @Nullable
  PsiElement getArithMinus();

  @Nullable
  PsiElement getAt();

  @Nullable
  PsiElement getDollar();

  @Nullable
  PsiElement getFiledescriptor();

  @Nullable
  PsiElement getGreaterThan();

  @Nullable
  PsiElement getLessThan();

  @Nullable
  PsiElement getRedirectGreaterAmp();

  @Nullable
  PsiElement getRedirectGreaterBar();

  @Nullable
  PsiElement getRedirectHereString();

  @Nullable
  PsiElement getRedirectLessAmp();

  @Nullable
  PsiElement getRedirectLessGreater();

  @Nullable
  PsiElement getShiftLeft();

  @Nullable
  PsiElement getShiftRight();

  @Nullable
  PsiElement getInt();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getWord();

}
