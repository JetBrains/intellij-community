// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.api;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

public final class MavenPluginConfigurationLanguageInjector implements LanguageInjector {

  @Override
  public void getLanguagesToInject(final @NotNull PsiLanguageInjectionHost host, final @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (!(host instanceof XmlText xmlText)) return;

    if (!MavenPluginParamInfo.isSimpleText(xmlText)) return;

    if (host.getContainingFile().getLanguage().is(HTMLLanguage.INSTANCE)) return;

    MavenPluginParamInfo.ParamInfoList infoList = MavenPluginParamInfo.getParamInfoList(xmlText);
    for (MavenPluginParamInfo.ParamInfo info : infoList) {
      Language language = info.getLanguage();

      if (language == null) {
        MavenParamLanguageProvider provider = info.getLanguageProvider();
        if (provider != null) {
          language = provider.getLanguage(xmlText, infoList.getDomCfg());
        }
      }

      if (language != null) {
        injectionPlacesRegistrar.addPlace(language, TextRange.from(0, host.getTextLength()), info.getLanguageInjectionPrefix(), info.getLanguageInjectionSuffix());
        return;
      }
    }
  }
}
