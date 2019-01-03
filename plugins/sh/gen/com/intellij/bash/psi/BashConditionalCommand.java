// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashConditionalCommand extends BashCommand {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashString> getStringList();

  @NotNull
  List<BashSubshellCommand> getSubshellCommandList();

  @Nullable
  PsiElement getExprConditionalLeft();

  @Nullable
  PsiElement getExprConditionalRight();

  @Nullable
  PsiElement getLeftDoubleBracket();

  @Nullable
  PsiElement getRightDoubleBracket();

}
