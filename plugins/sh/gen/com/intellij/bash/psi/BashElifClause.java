// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashElifClause extends BashCompositeElement {

  @NotNull
  List<BashCompoundList> getCompoundListList();

  @Nullable
  BashElifClause getElifClause();

  @NotNull
  PsiElement getElif();

  @Nullable
  PsiElement getElse();

  @NotNull
  PsiElement getThen();

}
