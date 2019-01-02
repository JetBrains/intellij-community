// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashIfCommand extends BashCommand {

  @NotNull
  List<BashCompoundList> getCompoundListList();

  @Nullable
  BashConditional getConditional();

  @Nullable
  BashElifClause getElifClause();

  @Nullable
  PsiElement getSemi();

  @Nullable
  PsiElement getElse();

  @Nullable
  PsiElement getFi();

  @NotNull
  PsiElement getIf();

  @Nullable
  PsiElement getThen();

}
