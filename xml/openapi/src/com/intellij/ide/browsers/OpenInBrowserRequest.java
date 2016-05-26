/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class OpenInBrowserRequest {
  private Collection<Url> result;
  protected PsiFile file;
  
  private boolean appendAccessToken = true;

  public OpenInBrowserRequest(@NotNull PsiFile file) {
    this.file = file;
  }

  public OpenInBrowserRequest() {
  }

  @Nullable
  public static OpenInBrowserRequest create(@NotNull final PsiElement element) {
    PsiFile psiFile;
    AccessToken token = ReadAction.start();
    try {
      psiFile = element.isValid() ? element.getContainingFile() : null;
      if (psiFile == null || psiFile.getVirtualFile() == null) {
        return null;
      }
    }
    finally {
      token.finish();
    }

    return new OpenInBrowserRequest(psiFile) {
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

  public boolean isAppendAccessToken() {
    return appendAccessToken;
  }

  public void setAppendAccessToken(boolean value) {
    this.appendAccessToken = value;
  }
}