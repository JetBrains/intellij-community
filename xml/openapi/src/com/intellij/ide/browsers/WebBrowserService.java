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
package com.intellij.ide.browsers;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class WebBrowserService {
  public static WebBrowserService getInstance() {
    return ServiceManager.getService(WebBrowserService.class);
  }

  @NotNull
  public abstract Collection<Url> getUrlsToOpen(@NotNull OpenInBrowserRequest request, boolean preferLocalUrl) throws WebBrowserUrlProvider.BrowserException;

  public static boolean isHtmlOrXmlLanguage(@NotNull Language language) {
    return language == HTMLLanguage.INSTANCE || language == XHTMLLanguage.INSTANCE || language == XMLLanguage.INSTANCE;
  }

  public static boolean isHtmlOrXmlFile(@NotNull PsiFile psiFile) {
    Language baseLanguage = psiFile.getViewProvider().getBaseLanguage();
    if (isHtmlOrXmlLanguage(baseLanguage)) {
      return true;
    }

    if (psiFile.getFileType() instanceof LanguageFileType) {
      return isHtmlOrXmlLanguage(((LanguageFileType)psiFile.getFileType()).getLanguage());
    }

    return false;
  }
}