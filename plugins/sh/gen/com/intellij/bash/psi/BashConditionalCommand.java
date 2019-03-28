// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashConditionalCommand extends BashCommand {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashBashExpansion> getBashExpansionList();

  @NotNull
  List<BashCommand> getCommandList();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashString> getStringList();

  @NotNull
  List<BashVariable> getVariableList();

  @Nullable
  PsiElement getExprConditionalLeft();

  @Nullable
  PsiElement getExprConditionalRight();

  @Nullable
  PsiElement getLeftDoubleBracket();

  @Nullable
  PsiElement getLeftSquare();

  @Nullable
  PsiElement getRightDoubleBracket();

  @Nullable
  PsiElement getRightSquare();

}
