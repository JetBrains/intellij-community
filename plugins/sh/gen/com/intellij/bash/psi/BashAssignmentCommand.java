// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashAssignmentCommand extends BashCommand {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashArrayExpression getArrayExpression();

  @Nullable
  BashAssignmentList getAssignmentList();

  @Nullable
  BashBashExpansion getBashExpansion();

  @Nullable
  BashShellParameterExpansion getShellParameterExpansion();

  @Nullable
  BashString getString();

  @Nullable
  BashVariable getVariable();

  @Nullable
  PsiElement getAddEq();

  @Nullable
  PsiElement getAt();

  @Nullable
  PsiElement getBang();

  @Nullable
  PsiElement getDollar();

  @Nullable
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

  @Nullable
  PsiElement getWord();

}
