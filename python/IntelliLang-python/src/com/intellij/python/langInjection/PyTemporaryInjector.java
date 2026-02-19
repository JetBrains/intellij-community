// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.langInjection;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.codeInsight.PyInjectionUtil;
import com.jetbrains.python.codeInsight.PyInjectorBase;
import com.jetbrains.python.psi.PyElement;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyTemporaryInjector extends PyInjectorBase {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PyInjectionUtil.InjectionResult result = registerInjection(registrar, context);
    if (result.isInjected()) {
      final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(context.getProject());
      InjectorUtils.registerSupport(registry.getLanguageInjectionSupport(), false, context, getInjectedLanguage(context));
    }
  }

  @Override
  public @Nullable Language getInjectedLanguage(@NotNull PsiElement context) {
    if (context instanceof PsiLanguageInjectionHost && context instanceof PyElement) {
      PsiFile file = context.getContainingFile();
      InjectedLanguage injectedLanguage = TemporaryPlacesRegistry.getInstance(file.getProject())
        .getLanguageFor((PsiLanguageInjectionHost)context, file);
      if (injectedLanguage != null) {
        return injectedLanguage.getLanguage();
      }
    }
    return null;
  }
}
