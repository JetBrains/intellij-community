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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.jetbrains.python.codeInsight.PyInjectionUtil;
import com.jetbrains.python.codeInsight.PyInjectorBase;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyTemporaryInjector extends PyInjectorBase {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PyInjectionUtil.InjectionResult result = registerInjection(registrar, context);
    if (result.isInjected()) {
      final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(context.getProject());
      InjectorUtils.registerSupport(registry.getLanguageInjectionSupport(), false, context, getInjectedLanguage(context));
      if (!result.isStrict()) {
        InjectorUtils.putInjectedFileUserData(context, getInjectedLanguage(context), InjectedLanguageUtil.FRANKENSTEIN_INJECTION, Boolean.TRUE);
      }
    }
  }

  @Nullable
  @Override
  public Language getInjectedLanguage(@NotNull PsiElement context) {
    final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(context.getProject());
    if (context instanceof PsiLanguageInjectionHost) {
      final PsiFile file = context.getContainingFile();
      final InjectedLanguage injectedLanguage = registry.getLanguageFor((PsiLanguageInjectionHost)context, file);
      if (injectedLanguage != null) {
        return injectedLanguage.getLanguage();
      }
    }
    return null;
  }
}
