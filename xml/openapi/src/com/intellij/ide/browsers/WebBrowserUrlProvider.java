/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public abstract class WebBrowserUrlProvider {
  public static ExtensionPointName<WebBrowserUrlProvider> EP_NAME = ExtensionPointName.create("com.intellij.webBrowserUrlProvider");

  /**
   * Browser exceptions are printed in Error Dialog when user presses any browser button.
   */
  public static class BrowserException extends Exception {
    public BrowserException(final String message) {
      super(message);
    }
  }

  /**
   * Invariant: element has not null containing psi file with not null virtual file 
   */
  @NotNull
  public abstract String getUrl(@NotNull PsiElement element, boolean shiftDown) throws Exception;

  /**
   * Invariant: element has not null containing psi file with not null virtual file
   */
  public abstract boolean canHandleElement(@NotNull final PsiElement element);

  @Nullable
  public String getOpenInBrowserActionText(@NotNull PsiFile file) {
    return null;
  }

  @Nullable
  public String getOpenInBrowserActionDescription(@NotNull PsiFile file) {
    return null;
  }
}
