/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xml.util.documentation.XmlDocumentationProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenPomXmlDocumentationProvider implements DocumentationProvider {

  private final DocumentationProvider myDelegate = new XmlDocumentationProvider() {
    @Override
    protected String generateDoc(String str, String name, String typeName, String version) {
      if (str != null) {
        str = StringUtil.unescapeXml(str);
      }

      return super.generateDoc(str, name, typeName, version);
    }
  };


  private static boolean isFromPomXml(PsiElement element) {
    if (element == null) return false;

    PsiFile containingFile = element.getContainingFile();
    return containingFile != null && containingFile.getName().equals("maven-4.0.0.xsd");
  }

  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (!isFromPomXml(element)) return null;

    return myDelegate.getQuickNavigateInfo(element, originalElement);
  }

  @Nullable
  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    if (!isFromPomXml(element)) return null;

    return myDelegate.getUrlFor(element, originalElement);
  }

  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!isFromPomXml(element)) return null;

    return myDelegate.generateDoc(element, originalElement);
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    if (!isFromPomXml(element)) return null;

    return myDelegate.getDocumentationElementForLookupItem(psiManager, object, element);
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }
}
