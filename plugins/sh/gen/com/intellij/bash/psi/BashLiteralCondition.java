// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashLiteralCondition extends BashCondition {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @Nullable
  BashBashExpansion getBashExpansion();

  @Nullable
  BashCommand getCommand();

  @Nullable
  BashNum getNum();

  @Nullable
  BashShellParameterExpansion getShellParameterExpansion();

  @Nullable
  BashString getString();

  @Nullable
  BashVariable getVariable();

  @Nullable
  PsiElement getBang();

  @Nullable
  PsiElement getDollar();

  @Nullable
  PsiElement getFiledescriptor();

  @Nullable
  PsiElement getWord();

}
