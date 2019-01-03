// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashIfCommand extends BashCommand {

  @Nullable
  BashCompoundList getCompoundList();

  @NotNull
  List<BashElifClause> getElifClauseList();

  @Nullable
  BashElseClause getElseClause();

  @Nullable
  BashThenClause getThenClause();

  @Nullable
  PsiElement getFi();

  @NotNull
  PsiElement getIf();

}
