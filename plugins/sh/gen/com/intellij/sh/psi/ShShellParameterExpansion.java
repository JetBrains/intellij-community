// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShShellParameterExpansion extends ShCompositeElement {

  @NotNull
  List<ShArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<ShShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<ShSubshellCommand> getSubshellCommandList();

  @NotNull
  PsiElement getLeftCurly();

  @Nullable
  PsiElement getRightCurly();

}
