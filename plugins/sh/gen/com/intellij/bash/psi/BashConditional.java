// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashConditional extends BashCompositeElement {

  @NotNull
  List<BashString> getStringList();

  @NotNull
  PsiElement getExprConditionalLeft();

  @Nullable
  PsiElement getExprConditionalRight();

}
