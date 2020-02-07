// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef;

import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider;
import org.jetbrains.annotations.NotNull;

public class JCEFHtmlPanelProvider extends MarkdownHtmlPanelProvider {

  @NotNull
  @Override
  public MarkdownHtmlPanel createHtmlPanel() {
    return new MarkdownJCEFHtmlPanel();
  }

  @NotNull
  @Override
  public AvailabilityInfo isAvailable() {
    try {
      // [tav] todo: we bundle jcef.jar with API in IDEA that anyway provides this class. So we should check for jcef module presence in JBR.
      if (Class.forName("org.cef.browser.CefBrowser", false, getClass().getClassLoader()) != null) {
        return AvailabilityInfo.AVAILABLE;
      }
    }
    catch (ClassNotFoundException ignored) {
    }

    return AvailabilityInfo.UNAVAILABLE;
  }

  @NotNull
  @Override
  public ProviderInfo getProviderInfo() {
    return new ProviderInfo("JCEF Browser", JCEFHtmlPanelProvider.class.getName());
  }
}
