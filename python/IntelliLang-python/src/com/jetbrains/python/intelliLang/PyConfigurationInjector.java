package com.jetbrains.python.intelliLang;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
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
