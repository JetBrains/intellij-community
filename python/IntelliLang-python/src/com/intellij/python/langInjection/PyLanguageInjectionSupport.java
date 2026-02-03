// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.langInjection;

import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.psi.PyElement;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public final class PyLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  private static final @NonNls String SUPPORT_ID = "python";

  @Override
  public @NotNull String getId() {
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

  @Override
  public @Nullable String getHelpId() {
    return "reference.settings.language.injection.generic.python";
  }
}
