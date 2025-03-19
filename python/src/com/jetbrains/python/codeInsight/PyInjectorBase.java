// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PyInjectorBase implements MultiHostInjector {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    registerInjection(registrar, context);
  }

  @Override
  public @NotNull List<Class<? extends PsiElement>> elementsToInjectIn() {
    return PyInjectionUtil.ELEMENTS_TO_INJECT_IN;
  }

  public abstract @Nullable Language getInjectedLanguage(@NotNull PsiElement context);

  protected PyInjectionUtil.InjectionResult registerInjection(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final Language language = getInjectedLanguage(context);
    if (language != null) {
      final PsiElement element = PyInjectionUtil.getLargestStringLiteral(context);
      if (element != null) {
        return PyInjectionUtil.registerStringLiteralInjection(element, registrar, language);
      }
    }
    return PyInjectionUtil.InjectionResult.EMPTY;
  }
}
