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
package com.intellij.ide.browsers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public abstract class WebBrowserUrlProvider {
  public static final ExtensionPointName<WebBrowserUrlProvider> EP_NAME = ExtensionPointName.create("com.intellij.webBrowserUrlProvider");

  /**
   * Browser exceptions are printed in Error Dialog when user presses any browser button
   */
  public static class BrowserException extends Exception {
    public BrowserException(final String message) {
      super(message);
    }
  }

  @Deprecated
  /**
   * @deprecated to remove in IDEA 14
   */
  public boolean canHandleElement(@NotNull PsiElement element, @NotNull PsiFile psiFile, @NotNull Ref<Set<Url>> result) {
    Ref<Collection<Url>> ref = Ref.create();
    boolean canHandle = canHandle(element, psiFile, ref);
    if (!ref.isNull()) {
      result.set(ContainerUtil.newHashSet(ref.get()));
    }
    return canHandle;
  }

  public boolean canHandle(@NotNull PsiElement element, @NotNull PsiFile psiFile, @NotNull Ref<Collection<Url>> result) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file == null) {
      return false;
    }

    try {
      Collection<Url> urls = getUrls(element, psiFile, file);
      if (!urls.isEmpty()) {
        result.set(urls);
        return true;
      }
    }
    catch (BrowserException ignored) {
    }

    return false;
  }

  @Nullable
  protected Url getUrl(@NotNull PsiElement element, @NotNull PsiFile psiFile, @NotNull VirtualFile virtualFile) throws BrowserException {
    return null;
  }

  public Collection<Url> getUrls(@NotNull PsiElement element, @NotNull PsiFile psiFile, @NotNull VirtualFile virtualFile) throws BrowserException {
    return ContainerUtil.createMaybeSingletonSet(getUrl(element, psiFile, virtualFile));
  }

  @Nullable
  public String getOpenInBrowserActionText(@NotNull PsiFile file) {
    return null;
  }

  @Nullable
  public String getOpenInBrowserActionDescription(@NotNull PsiFile file) {
    return null;
  }
}