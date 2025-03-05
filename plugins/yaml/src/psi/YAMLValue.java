package org.jetbrains.yaml.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface YAMLValue extends YAMLPsiElement {
  @Nullable
  PsiElement getTag();
}
