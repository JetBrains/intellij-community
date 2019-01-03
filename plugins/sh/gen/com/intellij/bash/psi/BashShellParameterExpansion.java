// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashShellParameterExpansion extends BashCompositeElement {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashCommand> getCommandList();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashString> getStringList();

  @Nullable
  PsiElement getArithMinus();

  @Nullable
  PsiElement getArithPlus();

  @Nullable
  PsiElement getColon();

  @NotNull
  PsiElement getLeftCurly();

  @NotNull
  PsiElement getRightCurly();

}
