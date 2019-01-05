// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashCaseClause extends BashCompositeElement {

  @Nullable
  BashCompoundList getCompoundList();

  @NotNull
  BashPattern getPattern();

  @Nullable
  PsiElement getLeftParen();

  @Nullable
  PsiElement getRightParen();

}
