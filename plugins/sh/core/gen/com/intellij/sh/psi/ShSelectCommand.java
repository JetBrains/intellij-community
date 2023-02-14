// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShSelectCommand extends ShCommand {

  @NotNull
  List<ShArithmeticExpansion> getArithmeticExpansionList();

  @Nullable
  ShBlock getBlock();

  @NotNull
  List<ShBraceExpansion> getBraceExpansionList();

  @NotNull
  List<ShCommand> getCommandList();

  @Nullable
  ShDoBlock getDoBlock();

  @Nullable
  ShListTerminator getListTerminator();

  @NotNull
  List<ShNumber> getNumberList();

  @NotNull
  List<ShShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<ShString> getStringList();

  @NotNull
  List<ShVariable> getVariableList();

  @Nullable
  PsiElement getIn();

  @NotNull
  PsiElement getSelect();

}
