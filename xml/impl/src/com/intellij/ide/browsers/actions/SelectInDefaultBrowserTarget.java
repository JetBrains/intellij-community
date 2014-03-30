/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.browsers.actions;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTargetBase;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;

final class SelectInDefaultBrowserTarget extends SelectInTargetBase {
  private static final Logger LOG = Logger.getInstance(SelectInDefaultBrowserTarget.class);

  @Override
  public boolean canSelect(SelectInContext context) {
    Object selectorInFile = context.getSelectorInFile();
    OpenInBrowserRequest request = selectorInFile instanceof PsiElement ? OpenInBrowserRequest.create((PsiElement)selectorInFile) : null;
    if (request == null) {
      return false;
    }

    WebBrowserUrlProvider urlProvider = WebBrowserServiceImpl.getProvider(request);
    if (urlProvider == null) {
      VirtualFile virtualFile = request.getVirtualFile();
      return virtualFile instanceof HttpVirtualFile || (HtmlUtil.isHtmlFile(request.getFile()) && !(virtualFile instanceof LightVirtualFile));
    }
    return true;
  }

  @Override
  public String toString() {
    return XmlBundle.message("browser.select.in.default.name");
  }

  @Override
  public void selectIn(SelectInContext context, boolean requestFocus) {
    PsiElement element = (PsiElement)context.getSelectorInFile();
    LOG.assertTrue(element != null);
    BaseOpenInBrowserAction.open(OpenInBrowserRequest.create(element), false, null);
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.OS_FILE_MANAGER;
  }
}
