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

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTargetBase;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Url;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;

import java.util.Set;

class SelectInDefaultBrowserTarget extends SelectInTargetBase {
  private static final Logger LOG = Logger.getInstance(SelectInDefaultBrowserTarget.class);

  private String currentName = "";

  @Override
  public boolean canSelect(SelectInContext context) {
    Object selectorInFile = context.getSelectorInFile();
    if (!(selectorInFile instanceof PsiElement)) {
      return false;
    }

    PsiFile file = ((PsiElement)selectorInFile).getContainingFile();
    if (file == null || file.getVirtualFile() == null) {
      return false;
    }

    Pair<WebBrowserUrlProvider, Set<Url>> browserUrlProvider = WebBrowserServiceImpl.getProvider(file);
    currentName = XmlBundle.message("browser.select.in.default.name");
    if (browserUrlProvider == null) {
      return HtmlUtil.isHtmlFile(file) && !(file.getVirtualFile() instanceof LightVirtualFile);
    }
    else {
      String customText = browserUrlProvider.first.getOpenInBrowserActionText(file);
      if (customText != null) {
        currentName = customText;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return currentName;
  }

  @Override
  public void selectIn(SelectInContext context, boolean requestFocus) {
    PsiElement psiElement = (PsiElement)context.getSelectorInFile();
    LOG.assertTrue(psiElement != null);
    PsiFile psiFile = psiElement.getContainingFile();
    LOG.assertTrue(psiFile != null);
    OpenFileInDefaultBrowserAction.doOpen(psiFile, false, null);
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.OS_FILE_MANAGER;
  }
}
