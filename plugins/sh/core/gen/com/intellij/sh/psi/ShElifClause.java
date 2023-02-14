// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShElifClause extends ShCompositeElement {

  @Nullable
  ShCompoundList getCompoundList();

  @Nullable
  ShThenClause getThenClause();

  @NotNull
  PsiElement getElif();

}
