// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashForClause extends BashCompositeElement {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashBashExpansion> getBashExpansionList();

  @NotNull
  List<BashCommand> getCommandList();

  @NotNull
  List<BashExpression> getExpressionList();

  @Nullable
  BashListTerminator getListTerminator();

  @NotNull
  List<BashNum> getNumList();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashString> getStringList();

  @NotNull
  List<BashVariable> getVariableList();

  @Nullable
  PsiElement getLeftDoubleParen();

  @Nullable
  PsiElement getRightDoubleParen();

}
