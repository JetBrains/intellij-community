package com.intellij.ide.browsers;

import com.intellij.lang.LanguageExtension;

/**
 * @author spleaner
 */
public class WebBrowserUrlProviders extends LanguageExtension<WebBrowserUrlProvider> {

  public static final WebBrowserUrlProviders INSTANCE = new WebBrowserUrlProviders();

  public WebBrowserUrlProviders() {
    super("com.intellij.webBrowserUrlProvider");
  }


}
