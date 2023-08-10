// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xml.util.documentation.XmlDocumentationProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MavenPomXmlDocumentationProvider implements DocumentationProvider {
  private final DocumentationProvider myDelegate = new XmlDocumentationProvider() {
    @Override
    protected String generateDoc(String str, String name, String typeName, String version) {
      if (str != null) {
        str = StringUtil.unescapeXmlEntities(str);
      }
      return super.generateDoc(str, name, typeName, version);
    }
  };

  private static boolean isFromPomXml(PsiElement element) {
    if (element == null) return false;

    PsiFile containingFile = element.getContainingFile();
    return containingFile != null && containingFile.getName().equals("maven-4.0.0.xsd");
  }

  @Override
  public @Nullable @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (!isFromPomXml(element)) return null;

    return myDelegate.getQuickNavigateInfo(element, originalElement);
  }

  @Override
  public @Nullable List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    if (!isFromPomXml(element)) return null;

    return myDelegate.getUrlFor(element, originalElement);
  }

  @Override
  public @Nullable @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!isFromPomXml(element)) return null;

    return myDelegate.generateDoc(element, originalElement);
  }

  @Override
  public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    if (!isFromPomXml(element)) return null;

    return myDelegate.getDocumentationElementForLookupItem(psiManager, object, element);
  }
}
