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

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class HtmlWebBrowserUrlProvider extends WebBrowserUrlProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.HtmlWebBrowserUrlProvider");

  @NotNull
  public String getUrl(@NotNull final PsiElement element, final boolean shiftDown) throws Exception {
    final PsiFile file = element instanceof PsiFile ? (PsiFile) element : element.getContainingFile();
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    return virtualFile.getUrl();
  }

  @Override
  public boolean canHandleElement(@NotNull final PsiElement element) {
    final PsiFile file = element instanceof PsiFile ? (PsiFile) element : element.getContainingFile();
    return file != null && isHtmlFile(file);
  }

  protected static boolean isHtmlFile(@NotNull final PsiFile file) {
    final Language language = file.getLanguage();
    return language instanceof HTMLLanguage || language instanceof XHTMLLanguage;
  }
}
