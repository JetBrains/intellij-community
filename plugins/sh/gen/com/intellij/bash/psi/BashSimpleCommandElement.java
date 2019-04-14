// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashSimpleCommandElement extends BashCompositeElement {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashBashExpansion getBashExpansion();

  @Nullable
  BashCommand getCommand();

  @Nullable
  BashHeredoc getHeredoc();

  @Nullable
  BashLiteral getLiteral();

  @Nullable
  BashOldArithmeticExpansion getOldArithmeticExpansion();

  @Nullable
  BashRedirection getRedirection();

  @Nullable
  BashShellParameterExpansion getShellParameterExpansion();

  @Nullable
  BashVariable getVariable();

  @Nullable
  PsiElement getBang();

  @Nullable
  PsiElement getDollar();

  @Nullable
  PsiElement getFiledescriptor();

}
