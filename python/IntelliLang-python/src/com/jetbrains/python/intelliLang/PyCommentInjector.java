package com.jetbrains.python.intelliLang;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyInjectorBase;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyCommentInjector extends PyInjectorBase {
  @Nullable
  @Override
  public Language getInjectedLanguage(@NotNull PsiElement context) {
    final BaseInjection injection = InjectorUtils.findCommentInjection(context, "comment", null);
    if (injection != null) {
      return InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
    }
    return null;
  }
}
