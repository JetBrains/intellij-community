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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class WebBrowserService {
  public static WebBrowserService getInstance() {
    return ServiceManager.getService(WebBrowserService.class);
  }

  public abstract boolean canOpenInBrowser(@NotNull PsiElement psiElement);

  @Nullable
  public abstract String getUrlToOpen(@NotNull PsiElement psiElement);

  @Nullable
  public abstract String getUrlToOpen(@NotNull PsiElement psiElement, boolean preferLocalUrl) throws WebBrowserUrlProvider.BrowserException;
}