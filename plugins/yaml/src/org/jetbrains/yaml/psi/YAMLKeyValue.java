package org.jetbrains.yaml.psi;

import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public interface YAMLKeyValue extends YAMLPsiElement, PsiNamedElement, PomTarget {
  YAMLKeyValue[] EMPTY_ARRAY = new YAMLKeyValue[0];

  @Nullable
  PsiElement getKey();

  @NotNull
  String getKeyText();

  @Nullable
  YAMLCompoundValue getValue();

  @NotNull
  String getValueText();

  void setValueText(String text);

  @NotNull
  String getValueIndent();
}