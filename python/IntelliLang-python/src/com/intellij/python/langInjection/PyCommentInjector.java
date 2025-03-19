// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.langInjection;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyInjectorBase;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class PyCommentInjector extends PyInjectorBase {
  @Override
  public @Nullable Language getInjectedLanguage(@NotNull PsiElement context) {
    final BaseInjection injection = InjectorUtils.findCommentInjection(context, "comment", null);
    if (injection != null) {
      return InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
    }
    return null;
  }
}
