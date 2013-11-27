/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public class WebBrowserServiceImpl extends WebBrowserService {
  @Override
  public boolean canOpenInBrowser(@NotNull PsiElement psiElement) {
    PsiFile psiFile = psiElement instanceof PsiFile ? (PsiFile)psiElement : psiElement.getContainingFile();
    VirtualFile virtualFile = psiFile == null ? null : psiFile.getVirtualFile();
    return virtualFile != null &&
           ((HtmlUtil.isHtmlFile(psiFile) && !(virtualFile instanceof LightVirtualFile)) || getProvider(psiElement, psiFile) != null);
  }

  @NotNull
  @Override
  public Set<Url> getUrlToOpen(@NotNull PsiElement psiElement, boolean preferLocalUrl) throws WebBrowserUrlProvider.BrowserException {
    final PsiFile psiFile = psiElement instanceof PsiFile ? (PsiFile)psiElement : psiElement.getContainingFile();
    if (psiFile == null) {
      return Collections.emptySet();
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return Collections.emptySet();
    }
    if (virtualFile instanceof HttpVirtualFile) {
      return Collections.singleton(Urls.newFromVirtualFile(virtualFile));
    }

    if (!(preferLocalUrl && HtmlUtil.isHtmlFile(psiFile))) {
      Pair<WebBrowserUrlProvider, Set<Url>> provider = getProvider(psiElement);
      if (provider != null) {
        if (provider.second != null) {
          return provider.second;
        }

        try {
          Set<Url> urls = provider.first.getUrls(psiElement, psiFile, virtualFile);
          if (!urls.isEmpty()) {
            return urls;
          }
        }
        catch (WebBrowserUrlProvider.BrowserException e) {
          if (!HtmlUtil.isHtmlFile(psiFile)) {
            throw e;
          }
        }
      }
    }
    return virtualFile instanceof LightVirtualFile ? Collections.<Url>emptySet() : Collections.singleton(Urls.newFromVirtualFile(virtualFile));
  }

  @Nullable
  public static Pair<WebBrowserUrlProvider, Set<Url>> getProvider(@Nullable PsiElement element) {
    PsiFile psiFile = element == null ? null : element.getContainingFile();
    return psiFile == null ? null : getProvider(element, psiFile);
  }

  private static Pair<WebBrowserUrlProvider, Set<Url>> getProvider(PsiElement element, PsiFile psiFile) {
    Ref<Set<Url>> result = Ref.create();
    DumbService dumbService = DumbService.getInstance(element.getProject());
    for (WebBrowserUrlProvider urlProvider : WebBrowserUrlProvider.EP_NAME.getExtensions()) {
      if ((!dumbService.isDumb() || DumbService.isDumbAware(urlProvider)) && urlProvider.canHandleElement(element, psiFile, result)) {
        return Pair.create(urlProvider, result.get());
      }
    }
    return null;
  }

  @Nullable
  public static Url getUrlForContext(@NotNull PsiElement sourceElement) {
    PsiFile psiFile = sourceElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }

    Url url;
    try {
      Set<Url> urls = WebBrowserService.getInstance().getUrlToOpen(sourceElement, false);
      url = ContainerUtil.getFirstItem(urls);
      if (url == null) {
        return null;
      }
    }
    catch (WebBrowserUrlProvider.BrowserException ignored) {
      return null;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }

    return !url.isInLocalFileSystem() || HtmlUtil.isHtmlFile(virtualFile) ? url : null;
  }
}
