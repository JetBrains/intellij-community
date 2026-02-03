// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

public final class HtmlCompatibleMetaLanguage extends MetaLanguage {
  private static final ExtensionPointName<HtmlCompatibleLanguageEP> EP_NAME =
    new ExtensionPointName<>("com.intellij.html.compatibleLanguage");
  private static final Supplier<Set<String>> LANGS = ExtensionPointUtil.dropLazyValueOnChange(
    new SynchronizedClearableLazy<>(() -> ContainerUtil.map2Set(EP_NAME.getExtensionList(), e -> e.language)), EP_NAME, null);

  private HtmlCompatibleMetaLanguage() {
    super("HtmlCompatible");
  }

  @Override
  public boolean matchesLanguage(@NotNull Language language) {
    Set<String> langs = LANGS.get();
    while (language != null) {
      if (langs.contains(language.getID())) return true;
      language = language.getBaseLanguage();
    }
    return false;
  }

  @ApiStatus.Experimental
  public static final class HtmlCompatibleLanguageEP extends BaseKeyedLazyInstance<String> {
    @Attribute("language")
    public String language;

    @Override
    protected @Nullable String getImplementationClassName() {
      return null;
    }

    @Override
    public @NotNull String createInstance(@NotNull ComponentManager componentManager,
                                          @NotNull PluginDescriptor pluginDescriptor) {
      return language;
    }
  }
}
