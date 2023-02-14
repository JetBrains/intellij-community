// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShShellParameterExpansion extends ShCompositeElement {

  @NotNull
  List<ShArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<ShArrayExpression> getArrayExpressionList();

  @NotNull
  List<ShBraceExpansion> getBraceExpansionList();

  @NotNull
  List<ShCommand> getCommandList();

  @NotNull
  List<ShNumber> getNumberList();

  @NotNull
  List<ShShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<ShString> getStringList();

  @NotNull
  List<ShVariable> getVariableList();

  @NotNull
  PsiElement getLeftCurly();

  @Nullable
  PsiElement getRightCurly();

}
