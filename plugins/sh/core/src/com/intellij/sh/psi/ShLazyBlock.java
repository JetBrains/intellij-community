package com.intellij.sh.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ShLazyBlock extends ShCompositeElement {
  @NotNull
  PsiElement getLeftCurly();

  @Nullable
  PsiElement getRightCurly();
}
