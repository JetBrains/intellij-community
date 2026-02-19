// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShIfCommand extends ShCommand {

  @Nullable
  ShCompoundList getCompoundList();

  @NotNull
  List<ShElifClause> getElifClauseList();

  @Nullable
  ShElseClause getElseClause();

  @Nullable
  ShThenClause getThenClause();

  @Nullable
  PsiElement getFi();

  @NotNull
  PsiElement getIf();

}
