// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LanguageHtmlScriptContentProvider extends LanguageExtension<HtmlScriptContentProvider> {
  public static final LanguageHtmlScriptContentProvider INSTANCE = new LanguageHtmlScriptContentProvider();

  public LanguageHtmlScriptContentProvider() {
    super("com.intellij.html.scriptContentProvider");
  }

  public static HtmlScriptContentProvider getScriptContentProvider(@NotNull Language language) {
    return INSTANCE.forLanguage(language);
  }

  public static @NotNull List<KeyedLazyInstance<HtmlScriptContentProvider>> getAllProviders() {
    return INSTANCE.getExtensions();
  }
}
