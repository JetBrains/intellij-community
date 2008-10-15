package com.intellij.ide.browsers;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author spleaner
 */
public class WebBrowserUrlProviders extends LanguageExtension<WebBrowserUrlProvider> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.WebBrowserUrlProviders");

  public static final WebBrowserUrlProviders INSTANCE = new WebBrowserUrlProviders();

  public WebBrowserUrlProviders() {
    super("com.intellij.webBrowserUrlProvider");
  }

  @Nullable
  public static WebBrowserUrlProvider getProvider(@NotNull PsiFile file) {
    final Language language = file.getViewProvider().getBaseLanguage();
    final List<WebBrowserUrlProvider> providers = WebBrowserUrlProviders.INSTANCE.forKey(language);
    if (providers.size() > 0) {
      LOG.assertTrue(providers.size() == 1, "Only one WebBrowserUrlProvider per language (" + language.getDisplayName() + ") is allowed!");
      return providers.get(0);
    }

    return null;
  }

}
