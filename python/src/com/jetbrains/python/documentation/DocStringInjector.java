package com.jetbrains.python.documentation;

import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class DocStringInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {

  }
}
