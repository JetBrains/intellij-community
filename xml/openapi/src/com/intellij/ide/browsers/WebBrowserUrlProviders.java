package com.intellij.ide.browsers;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class WebBrowserUrlProviders  {
  private WebBrowserUrlProviders() {
  }

  @Nullable
  public static WebBrowserUrlProvider getProvider(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    
    final WebBrowserUrlProvider[] urlProviders = WebBrowserUrlProvider.EP_NAME.getExtensions();
    for (WebBrowserUrlProvider urlProvider : urlProviders) {
      if (urlProvider.canHandleElement(element)) {
        return urlProvider;
      }
    }

    return null;
  }

}
