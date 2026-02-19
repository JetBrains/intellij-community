// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.langInjection;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyInjectorBase;
import com.jetbrains.python.psi.PyElement;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyConfigurationInjector extends PyInjectorBase {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!(context instanceof PyElement)) return;
    registerInjection(registrar, context);
  }

  @Override
  public @Nullable Language getInjectedLanguage(@NotNull PsiElement context) {
    for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
      if (support instanceof PyLanguageInjectionSupport) {
        final Configuration configuration = Configuration.getInstance();
        for (BaseInjection injection : configuration.getInjections(support.getId())) {
          if (injection.acceptsPsiElement(context)) {
            return InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          }
        }
      }
    }
    return null;
  }
}
