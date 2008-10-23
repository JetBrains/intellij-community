package com.intellij.ide.browsers;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class WebBrowserUrlProviders  {
  private WebBrowserUrlProviders() {
  }

  @Nullable
  public static WebBrowserUrlProvider getProvider(@Nullable PsiFile file) {
    if (file == null) {
      return null;
    }
    
    final WebBrowserUrlProvider[] urlProviders = WebBrowserUrlProvider.EP_NAME.getExtensions();
    for (WebBrowserUrlProvider urlProvider : urlProviders) {
      if (urlProvider.canHandleFile(file)) {
        return urlProvider;
      }
    }

    return null;
  }

}
