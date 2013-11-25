package com.jetbrains.python.intelliLang;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
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
    if (registerInjection(registrar, context)) {
      final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(context.getProject());
      InjectorUtils.registerSupport(registry.getLanguageInjectionSupport(), false, registrar);
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
