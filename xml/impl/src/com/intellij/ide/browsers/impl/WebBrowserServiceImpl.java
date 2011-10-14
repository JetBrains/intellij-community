/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.browsers.WebBrowserService;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class WebBrowserServiceImpl extends WebBrowserService {
  @Override
  public boolean canOpenInBrowser(@NotNull PsiElement psiElement) {
    final PsiFile psiFile = psiElement instanceof PsiFile ? (PsiFile)psiElement : psiElement.getContainingFile();
    return psiFile != null && psiFile.getVirtualFile() != null &&
           (HtmlUtil.isHtmlFile(psiFile) || getProvider(psiElement) != null);
  }

  @Override
  @Nullable
  public String getUrlToOpen(@NotNull PsiElement psiElement, boolean preferLocalUrl) throws WebBrowserUrlProvider.BrowserException {
    final PsiFile psiFile = psiElement instanceof PsiFile ? (PsiFile)psiElement : psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    final String localUrl = virtualFile.getUrl();
    if (virtualFile instanceof HttpVirtualFile) {
      return localUrl;
    }

    if (preferLocalUrl && HtmlUtil.isHtmlFile(psiFile)) {
      return localUrl;
    }

    final WebBrowserUrlProvider provider = getProvider(psiElement);
    if (provider == null) {
      return localUrl;
    }
    try {
      return provider.getUrl(psiElement);
    }
    catch (WebBrowserUrlProvider.BrowserException e) {
      if (HtmlUtil.isHtmlFile(psiFile)) {
        return localUrl;
      }
      throw e;
    }
  }

  @Nullable
  public String getUrlToOpen(@NotNull PsiElement psiElement) {
    try {
      return getUrlToOpen(psiElement, false);
    }
    catch (WebBrowserUrlProvider.BrowserException e) {
      return null;
    }
  }

  @Nullable
  public static WebBrowserUrlProvider getProvider(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }

    final List<WebBrowserUrlProvider> allProviders = Arrays.asList(WebBrowserUrlProvider.EP_NAME.getExtensions());
    for (WebBrowserUrlProvider urlProvider : DumbService.getInstance(element.getProject()).filterByDumbAwareness(allProviders)) {
      if (urlProvider.canHandleElement(element)) {
        return urlProvider;
      }
    }

    return null;
  }
}
