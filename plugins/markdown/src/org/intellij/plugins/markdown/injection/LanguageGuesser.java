// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection;

import com.intellij.lang.Language;
import com.intellij.lexer.EmbeddedTokenTypesProvider;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum LanguageGuesser {
  INSTANCE;

  private final NotNullLazyValue<List<EmbeddedTokenTypesProvider>> embeddedTokenTypeProviders =
    new NotNullLazyValue<List<EmbeddedTokenTypesProvider>>() {
      @NotNull
      @Override
      protected List<EmbeddedTokenTypesProvider> compute() {
        return EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME.getExtensionList();
      }
    };

  private final NotNullLazyValue<Map<String, Language>> langIdToLanguage = new NotNullLazyValue<Map<String, Language>>() {
    @NotNull
    @Override
    protected Map<String, Language> compute() {
      final HashMap<String, Language> result = new HashMap<>();
      for (Language language : Language.getRegisteredLanguages()) {
        if (language.getID().isEmpty()) {
          continue;
        }

        result.put(StringUtil.toLowerCase(language.getID()), language);
      }

      final Language javascriptLanguage = result.get("javascript");
      if (javascriptLanguage != null) {
        result.put("js", javascriptLanguage);
      }
      return result;
    }
  };

  @NotNull
  public Map<String, Language> getLangToLanguageMap() {
    return Collections.unmodifiableMap(langIdToLanguage.getValue());
  }

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

    final Language languageFromMap = langIdToLanguage.getValue().get(StringUtil.toLowerCase(languageName));
    if (languageFromMap != null) {
      return languageFromMap;
    }

    for (EmbeddedTokenTypesProvider provider : embeddedTokenTypeProviders.getValue()) {
      if (provider.getName().equalsIgnoreCase(languageName)) {
        return provider.getElementType().getLanguage();
      }
    }
    return null;
  }
}
