// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShForClause extends ShCompositeElement {

  @NotNull
  List<ShArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<ShBashExpansion> getBashExpansionList();

  @NotNull
  List<ShCommand> getCommandList();

  @NotNull
  List<ShExpression> getExpressionList();

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
  PsiElement getLeftDoubleParen();

  @Nullable
  PsiElement getRightDoubleParen();

}
