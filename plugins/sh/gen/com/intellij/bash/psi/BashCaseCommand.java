// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashCaseCommand extends BashCommand {

  @NotNull
  List<BashCaseClause> getCaseClauseList();

  @NotNull
  PsiElement getCase();

  @Nullable
  PsiElement getEsac();

  @Nullable
  PsiElement getVariable();

  @Nullable
  PsiElement getWord();

}
