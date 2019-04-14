// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashRedirection extends BashCompositeElement {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashBashExpansion> getBashExpansionList();

  @NotNull
  List<BashCommand> getCommandList();

  @NotNull
  List<BashNumber> getNumberList();

  @Nullable
  BashProcessSubstitution getProcessSubstitution();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashString> getStringList();

  @NotNull
  List<BashVariable> getVariableList();

  @Nullable
  PsiElement getGt();

  @Nullable
  PsiElement getLt();

  @Nullable
  PsiElement getMinus();

  @Nullable
  PsiElement getRedirectAmpGreater();

  @Nullable
  PsiElement getRedirectAmpGreaterGreater();

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

}
