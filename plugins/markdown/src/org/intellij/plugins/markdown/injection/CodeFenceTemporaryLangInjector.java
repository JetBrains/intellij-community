package org.intellij.plugins.markdown.injection;

import com.intellij.lang.Language;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeFenceTemporaryLangInjector extends CodeFenceInjector {

  @Nullable
  @Override
  protected Language findLangForInjection(@NotNull MarkdownCodeFenceImpl element) {
    final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(element.getProject());
    final InjectedLanguage language = registry.getLanguageFor(element, element.getContainingFile());
    if (language != null) {
      return language.getLanguage();
    }
    return null;
  }
}
