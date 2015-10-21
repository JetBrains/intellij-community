/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.browsers.impl;

import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.ide.browsers.WebBrowserService;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class WebBrowserServiceImpl extends WebBrowserService {
  @NotNull
  @Override
  public Collection<Url> getUrlsToOpen(@NotNull OpenInBrowserRequest request, boolean preferLocalUrl) throws WebBrowserUrlProvider.BrowserException {
    boolean isHtmlOrXml = isHtmlOrXmlFile(request.getFile().getViewProvider().getBaseLanguage());
    if (!preferLocalUrl || !isHtmlOrXml) {
      DumbService dumbService = DumbService.getInstance(request.getProject());
      for (WebBrowserUrlProvider urlProvider : WebBrowserUrlProvider.EP_NAME.getExtensions()) {
        if ((!dumbService.isDumb() || DumbService.isDumbAware(urlProvider)) && urlProvider.canHandleElement(request)) {
          Collection<Url> urls = getUrls(urlProvider, request);
          if (!urls.isEmpty()) {
            return urls;
          }
        }
      }

      if (!isHtmlOrXml) {
        return Collections.emptyList();
      }
    }

    VirtualFile file = request.getVirtualFile();
    return file instanceof LightVirtualFile || !request.getFile().getViewProvider().isPhysical()
           ? Collections.<Url>emptyList()
           : Collections.singletonList(Urls.newFromVirtualFile(file));
  }

  @NotNull
  private static Collection<Url> getUrls(@Nullable WebBrowserUrlProvider provider, @NotNull OpenInBrowserRequest request) throws WebBrowserUrlProvider.BrowserException {
    if (provider != null) {
      if (request.getResult() != null) {
        return request.getResult();
      }

      try {
        return provider.getUrls(request);
      }
      catch (WebBrowserUrlProvider.BrowserException e) {
        if (!HtmlUtil.isHtmlFile(request.getFile())) {
          throw e;
        }
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static WebBrowserUrlProvider getProvider(@NotNull OpenInBrowserRequest request) {
    DumbService dumbService = DumbService.getInstance(request.getProject());
    for (WebBrowserUrlProvider urlProvider : WebBrowserUrlProvider.EP_NAME.getExtensions()) {
      if ((!dumbService.isDumb() || DumbService.isDumbAware(urlProvider)) && urlProvider.canHandleElement(request)) {
        return urlProvider;
      }
    }
    return null;
  }

  @NotNull
  public static Collection<Url> getDebuggableUrls(@Nullable PsiElement context) {
    try {
      OpenInBrowserRequest request = context == null ? null : OpenInBrowserRequest.create(context);
      return request == null || request.getFile().getViewProvider().getBaseLanguage() == XMLLanguage.INSTANCE ? Collections.<Url>emptyList() : getUrls(getProvider(request), request);
    }
    catch (WebBrowserUrlProvider.BrowserException ignored) {
      return Collections.emptyList();
    }
  }

  @Nullable
  public static Url getDebuggableUrl(@Nullable PsiElement context) {
    return ContainerUtil.getFirstItem(getDebuggableUrls(context));
  }
}
