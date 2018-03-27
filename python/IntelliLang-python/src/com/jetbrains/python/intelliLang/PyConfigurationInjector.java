/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.intelliLang;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.jetbrains.python.codeInsight.PyInjectionUtil;
import com.jetbrains.python.codeInsight.PyInjectorBase;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyConfigurationInjector extends PyInjectorBase {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PyInjectionUtil.InjectionResult result = registerInjection(registrar, context);
    if (!result.isStrict()) {
      InjectorUtils.putInjectedFileUserData(context, getInjectedLanguage(context), InjectedLanguageUtil.FRANKENSTEIN_INJECTION, Boolean.TRUE);
    }
  }

  @Nullable
  @Override
  public Language getInjectedLanguage(@NotNull PsiElement context) {
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
