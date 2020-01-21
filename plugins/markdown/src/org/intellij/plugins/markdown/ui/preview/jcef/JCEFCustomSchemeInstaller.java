// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefFileSchemeHandler;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;
import org.intellij.plugins.markdown.ui.preview.IntelliJImageGeneratingProviderKt;
import org.intellij.plugins.markdown.ui.preview.MarkdownAccessor;
import org.jetbrains.annotations.NotNull;

/**
 * Installs a custom scheme for loading local image files.
 */
public class JCEFCustomSchemeInstaller {
  private static final String MD_FILE_SCHEME_NAME = "jcef-md-image";

  JCEFCustomSchemeInstaller() {
    if (!Registry.is("ide.browser.jcef.enabled")) return;

    IntelliJImageGeneratingProviderKt.registerSchemeReplacement("file", MD_FILE_SCHEME_NAME);

    JBCefApp.addCefSchemeHandlerFactory(new JBCefApp.JBCefSchemeHandlerFactory() {
      @Override
      public void registerCustomScheme(@NotNull CefSchemeRegistrar registrar) {
        registrar.addCustomScheme(MD_FILE_SCHEME_NAME,
                                  true,
                                  false,
                                  false,
                                  true,
                                  false,
                                  true, // bypass CSP
                                  false);
      }

      @Override
      public String getSchemeName() {
        return MD_FILE_SCHEME_NAME;
      }

      @Override
      public String getDomainName() {
        return "";
      }

      @Override
      public CefResourceHandler create(CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
        return schemeName.equals(MD_FILE_SCHEME_NAME) ?
               JBCefFileSchemeHandler.create(MD_FILE_SCHEME_NAME,
                                             (path) -> MarkdownAccessor.getSafeOpenerAccessor().isSafeExtension(path)) :
               null;
      }
    });
  }
}
