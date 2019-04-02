// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashRedirection extends BashCompositeElement {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashBashExpansion getBashExpansion();

  @Nullable
  BashCommand getCommand();

  @Nullable
  BashShellParameterExpansion getShellParameterExpansion();

  @Nullable
  BashString getString();

  @Nullable
  BashVariable getVariable();

  @Nullable
  PsiElement getAt();

  @Nullable
  PsiElement getBang();

  @Nullable
  PsiElement getDollar();

  @Nullable
  PsiElement getFiledescriptor();

  @Nullable
  PsiElement getGt();

  @Nullable
  PsiElement getLt();

  @Nullable
  PsiElement getMinus();

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
  PsiElement getWord();

}
