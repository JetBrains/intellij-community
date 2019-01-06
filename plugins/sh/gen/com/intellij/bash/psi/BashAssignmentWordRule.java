// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashAssignmentWordRule extends BashCompositeElement {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashAssignmentList getAssignmentList();

  @Nullable
  BashCommand getCommand();

  @Nullable
  BashShellParameterExpansion getShellParameterExpansion();

  @Nullable
  BashString getString();

  @NotNull
  List<BashVariable> getVariableList();

  @Nullable
  PsiElement getAt();

  @Nullable
  PsiElement getBang();

  @Nullable
  PsiElement getDollar();

  @NotNull
  PsiElement getEq();

  @Nullable
  PsiElement getFiledescriptor();

  @Nullable
  PsiElement getAssignmentWord();

  @Nullable
  PsiElement getHex();

  @Nullable
  PsiElement getInt();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getOctal();

}
