// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShRedirection extends ShCompositeElement {

  @NotNull
  List<ShArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<ShBraceExpansion> getBraceExpansionList();

  @NotNull
  List<ShCommand> getCommandList();

  @NotNull
  List<ShNumber> getNumberList();

  @Nullable
  ShProcessSubstitution getProcessSubstitution();

  @NotNull
  List<ShShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<ShString> getStringList();

  @NotNull
  List<ShVariable> getVariableList();

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
