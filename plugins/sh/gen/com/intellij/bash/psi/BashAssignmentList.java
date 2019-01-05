// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashAssignmentList extends BashCompositeElement {

  @NotNull
  List<BashArrayAssignment> getArrayAssignmentList();

  @NotNull
  PsiElement getLeftParen();

  @Nullable
  PsiElement getRightParen();

}
