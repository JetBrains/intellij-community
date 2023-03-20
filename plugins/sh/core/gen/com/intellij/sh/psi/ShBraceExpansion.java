// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShBraceExpansion extends ShCompositeElement {

  @NotNull
  List<ShBraceExpansion> getBraceExpansionList();

  @NotNull
  PsiElement getLeftCurly();

  @NotNull
  PsiElement getRightCurly();

}
