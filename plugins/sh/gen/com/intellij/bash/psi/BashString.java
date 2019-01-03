// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashString extends BashCompositeElement {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashSubshellCommand> getSubshellCommandList();

  @Nullable
  PsiElement getString2();

  @Nullable
  PsiElement getStringBegin();

  @Nullable
  PsiElement getStringEnd();

}
