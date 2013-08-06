package com.jetbrains.python.intelliLang;

import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.patterns.PythonPatterns;
import com.jetbrains.python.psi.PyElement;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  @NonNls private static final String SUPPORT_ID = "python";

  @NotNull
  @Override
  public String getId() {
    return SUPPORT_ID;
  }

  @NotNull
  @Override
  public Class[] getPatternClasses() {
    return new Class[] { PythonPatterns.class };
  }

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return host instanceof PyElement;
  }

  @Override
  public boolean useDefaultInjector(PsiLanguageInjectionHost host) {
    return true;
  }
}
