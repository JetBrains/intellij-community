// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashCaseCommand extends BashCommand {

  @Nullable
  BashArithmeticExpansion getArithmeticExpansion();

  @NotNull
  List<BashCaseClause> getCaseClauseList();

  @Nullable
  BashShellParameterExpansion getShellParameterExpansion();

  @Nullable
  BashString getString();

  @Nullable
  BashVariable getVariable();

  @Nullable
  PsiElement getDollar();

  @Nullable
  PsiElement getFiledescriptor();

  @NotNull
  PsiElement getCase();

  @Nullable
  PsiElement getEsac();

  @Nullable
  PsiElement getInt();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getWord();

}
