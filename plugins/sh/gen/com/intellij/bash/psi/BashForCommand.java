// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashForCommand extends BashCompositeElement {

  @NotNull
  BashCompoundList getCompoundList();

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

  @NotNull
  PsiElement getFor();

  @Nullable
  PsiElement getIn();

}
