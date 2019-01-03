// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashSimpleCommandElement extends BashCompositeElement {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashAssignmentWordRule getAssignmentWordRule();

  @Nullable
  BashCommand getCommand();

  @Nullable
  BashHeredoc getHeredoc();

  @Nullable
  BashRedirection getRedirection();

  @Nullable
  BashShellParameterExpansion getShellParameterExpansion();

  @Nullable
  BashString getString();

  @Nullable
  PsiElement getDollar();

  @Nullable
  PsiElement getInt();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getVariable();

  @Nullable
  PsiElement getWord();

}
