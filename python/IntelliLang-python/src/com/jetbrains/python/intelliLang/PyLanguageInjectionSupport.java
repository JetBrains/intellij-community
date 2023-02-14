// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intelliLang;

import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.patterns.PythonPatterns;
import com.jetbrains.python.psi.PyElement;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  @NonNls private static final String SUPPORT_ID = "python";

  @NotNull
  @Override
  public String getId() {
    return SUPPORT_ID;
  }

  @Override
  public Class @NotNull [] getPatternClasses() {
    return new Class[] { PythonPatterns.class };
  }

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return host instanceof PyElement;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return "reference.settings.language.injection.generic.python";
  }
}
