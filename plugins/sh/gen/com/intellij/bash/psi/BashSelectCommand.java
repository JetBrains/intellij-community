// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashSelectCommand extends BashCommand {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashCommand> getCommandList();

  @Nullable
  BashListTerminator getListTerminator();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashString> getStringList();

  @NotNull
  List<BashVariable> getVariableList();

  @Nullable
  PsiElement getSemi();

  @NotNull
  PsiElement getSelect();

}
