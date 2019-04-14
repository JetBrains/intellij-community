// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashCaseCommand extends BashCommand {

  @NotNull
  List<BashArithmeticExpansion> getArithmeticExpansionList();

  @NotNull
  List<BashBashExpansion> getBashExpansionList();

  @NotNull
  List<BashCaseClause> getCaseClauseList();

  @NotNull
  List<BashCommand> getCommandList();

  @NotNull
  List<BashNum> getNumList();

  @NotNull
  List<BashShellParameterExpansion> getShellParameterExpansionList();

  @NotNull
  List<BashString> getStringList();

  @NotNull
  List<BashVariable> getVariableList();

  @NotNull
  PsiElement getCase();

  @Nullable
  PsiElement getEsac();

  @Nullable
  PsiElement getIn();

}
