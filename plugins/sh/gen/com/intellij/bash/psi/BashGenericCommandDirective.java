// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashGenericCommandDirective extends BashSimpleCommand {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashBashExpansion getBashExpansion();

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
