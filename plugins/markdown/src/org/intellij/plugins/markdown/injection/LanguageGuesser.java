// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection;

import com.intellij.lang.Language;
import com.intellij.lexer.EmbeddedTokenTypesProvider;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum LanguageGuesser {
  INSTANCE;


  @NotNull
  public List<CodeFenceLanguageProvider> getCodeFenceLanguageProviders() {
    return CodeFenceLanguageProvider.EP_NAME.getExtensionList();
  }

  @Nullable
  public Language guessLanguage(@NotNull String languageName) {
    for (CodeFenceLanguageProvider provider : getCodeFenceLanguageProviders()) {
      final Language languageByProvider = provider.getLanguageByInfoString(languageName);
      if (languageByProvider != null) {
        return languageByProvider;
      }
    }

    String lowerCasedLanguageName = StringUtil.toLowerCase(languageName);
    Language candidate = Language.findLanguageByID(lowerCasedLanguageName);
    if (candidate != null) {
      return candidate;
    }
    for (Language language : Language.getRegisteredLanguages()) {
      if (StringUtil.toLowerCase(language.getID()).equals(lowerCasedLanguageName)) {
        return language;
      }
    }

    for (EmbeddedTokenTypesProvider provider : EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      if (provider.getName().equalsIgnoreCase(languageName)) {
        return provider.getElementType().getLanguage();
      }
    }
    return null;
  }
}
