// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashIfCommand extends BashCompositeElement {

  @NotNull
  List<BashCompoundList> getCompoundListList();

  @Nullable
  BashElifClause getElifClause();

  @Nullable
  PsiElement getElse();

  @NotNull
  PsiElement getFi();

  @NotNull
  PsiElement getIf();

  @NotNull
  PsiElement getThen();

}
