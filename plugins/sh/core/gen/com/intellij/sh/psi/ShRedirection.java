// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShRedirection extends ShCompositeElement {

  @Nullable
  ShArithmeticExpansion getArithmeticExpansion();

  @Nullable
  ShBraceExpansion getBraceExpansion();

  @NotNull
  List<ShCommand> getCommandList();

  @Nullable
  ShNumber getNumber();

  @Nullable
  ShProcessSubstitution getProcessSubstitution();

  @Nullable
  ShShellParameterExpansion getShellParameterExpansion();

  @Nullable
  ShString getString();

  @Nullable
  ShVariable getVariable();

}
