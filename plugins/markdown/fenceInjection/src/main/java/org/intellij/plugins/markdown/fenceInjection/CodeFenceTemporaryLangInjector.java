package org.intellij.plugins.markdown.fenceInjection;

import com.intellij.lang.Language;
import kotlin.Pair;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CodeFenceTemporaryLangInjector extends CodeFenceInjector {
  @Override
  protected @Nullable Pair<Language, String> findLangForInjection(@NotNull MarkdownCodeFence element) {
    final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(element.getProject());
    final InjectedLanguage language = registry.getLanguageFor(element, element.getContainingFile());
    if (language != null && language.getLanguage() != null) {
      return new Pair<>(language.getLanguage(), null);
    }
    return null;
  }
}
