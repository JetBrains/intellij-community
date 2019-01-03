// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashHeredoc extends BashCompositeElement {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashSubshellCommand> getSubshellCommandList();

  @NotNull
  PsiElement getHeredocMarkerEnd();

  @NotNull
  PsiElement getHeredocMarkerStart();

  @NotNull
  PsiElement getHeredocMarkerTag();

}
