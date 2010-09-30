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
package com.intellij.util.xml;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class DomDocumentationProvider implements DocumentationProvider {

  public String getQuickNavigateInfo(final PsiElement element, PsiElement originalElement) {
    return null;
  }

  public List<String> getUrlFor(final PsiElement element, final PsiElement originalElement) {
    return null;
  }

  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    final DomElement domElement = DomUtil.getDomElement(element);
    return domElement == null ? null : ElementPresentationManagerImpl.getDocumentationForElement(domElement);
  }

  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    return null;
  }

  public PsiElement getDocumentationElementForLink(final PsiManager psiManager, final String link, final PsiElement context) {
    return null;
  }
}
