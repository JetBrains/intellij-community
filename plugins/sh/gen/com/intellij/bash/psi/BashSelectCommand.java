// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashSelectCommand extends BashCompositeElement {

  @NotNull
  BashList getList();

  @Nullable
  BashListTerminator getListTerminator();

  @Nullable
  PsiElement getLeftCurly();

  @Nullable
  PsiElement getRightCurly();

  @Nullable
  PsiElement getSemi();

  @Nullable
  PsiElement getDo();

  @Nullable
  PsiElement getDone();

  @Nullable
  PsiElement getIn();

  @NotNull
  PsiElement getSelect();

}
