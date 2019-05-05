// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShString extends ShLiteral {

  @NotNull
  List<ShArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<ShCommand> getCommandList();

  @NotNull
  List<ShShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<ShVariable> getVariableList();

  @Nullable
  PsiElement getRawString();

  //WARNING: getReferences(...) is skipped
  //matching getReferences(ShString, ...)
  //methods are not found in ShPsiImplUtil

}
