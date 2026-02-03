package com.intellij.sh.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ShLazyDoBlock extends ShCompositeElement {
  @NotNull
  PsiElement getDo();

  @Nullable
  PsiElement getDone();
}
