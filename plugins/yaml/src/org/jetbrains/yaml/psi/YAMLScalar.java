package org.jetbrains.yaml.psi;

import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

public interface YAMLScalar extends YAMLValue, PsiLanguageInjectionHost {
  @NotNull
  String getTextValue();

  boolean isMultiline();
}
