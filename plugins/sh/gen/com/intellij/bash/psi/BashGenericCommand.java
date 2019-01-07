// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashGenericCommand extends BashSimpleCommand {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashAssignmentWordRule getAssignmentWordRule();

  @Nullable
  BashBashExpansion getBashExpansion();

  @Nullable
  BashHeredoc getHeredoc();

  @Nullable
  BashRedirection getRedirection();

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
  PsiElement getHex();

  @Nullable
  PsiElement getInt();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getOctal();

  @Nullable
  PsiElement getWord();

}
