// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashPatternList extends BashCompositeElement {

  @Nullable
  BashCompoundList getCompoundList();

  @NotNull
  BashPattern getPattern();

  @Nullable
  PsiElement getLeftParen();

  @NotNull
  PsiElement getRightParen();

}
