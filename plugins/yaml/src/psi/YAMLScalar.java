package org.jetbrains.yaml.psi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

public interface YAMLScalar extends YAMLValue, PsiLanguageInjectionHost {
  @NotNull
  @NlsSafe
  String getTextValue();

  boolean isMultiline();
}
