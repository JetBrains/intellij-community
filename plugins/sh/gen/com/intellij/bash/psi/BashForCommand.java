// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashForCommand extends BashCommand {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @Nullable
  BashBlock getBlock();

  @NotNull
  List<BashCommand> getCommandList();

  @NotNull
  List<BashExpression> getExpressionList();

  @Nullable
  BashListTerminator getListTerminator();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashString> getStringList();

  @NotNull
  PsiElement getFor();

}
