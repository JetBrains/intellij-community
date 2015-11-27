package org.jetbrains.yaml.psi;

import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public interface YAMLKeyValue extends YAMLPsiElement, PsiNamedElement, PomTarget {
  @Contract(pure = true)
  @Nullable
  PsiElement getKey();

  @Contract(pure = true)
  @NotNull
  String getKeyText();

  @Contract(pure = true)
  @Nullable
  YAMLValue getValue();

  @Contract(pure = true)
  @NotNull
  String getValueText();
  
  YAMLMapping getParentMapping();

  void setValue(@NotNull YAMLValue value);
}