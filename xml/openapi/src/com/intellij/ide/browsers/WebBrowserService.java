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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class WebBrowserService {
  public static WebBrowserService getInstance() {
    return ServiceManager.getService(WebBrowserService.class);
  }

  @NotNull
  public abstract Collection<Url> getUrlsToOpen(@NotNull CanHandleElementRequest request, boolean preferLocalUrl) throws WebBrowserUrlProvider.BrowserException;

  @NotNull
  public Collection<Url> getUrlsToOpen(@NotNull final PsiElement element, boolean preferLocalUrl) throws WebBrowserUrlProvider.BrowserException {
    CanHandleElementRequest request = CanHandleElementRequest.createRequest(element);
    return request == null ? Collections.<Url>emptyList() : getUrlsToOpen(request, preferLocalUrl);
  }

  public abstract static class CanHandleElementRequest {
    private Collection<Url> result;
    protected PsiFile file;

    protected CanHandleElementRequest(@NotNull PsiFile file) {
      this.file = file;
    }

    protected CanHandleElementRequest() {
    }

    @Nullable
    public static CanHandleElementRequest createRequest(@NotNull final PsiElement element) {
      PsiFile psiFile = element.getContainingFile();
      if (psiFile == null) {
        return null;
      }

      return new CanHandleElementRequest(psiFile) {
        @NotNull
        @Override
        public PsiElement getElement() {
          return element;
        }
      };
    }

    @NotNull
    public PsiFile getFile() {
      return file;
    }

    @NotNull
    public VirtualFile getVirtualFile() {
      return file.getVirtualFile();
    }

    @NotNull
    public Project getProject() {
      return file.getProject();
    }

    @NotNull
    public abstract PsiElement getElement();

    public void setResult(@NotNull Collection<Url> result) {
      this.result = result;
    }

    @Nullable
    public Collection<Url> getResult() {
      return result;
    }
  }
}