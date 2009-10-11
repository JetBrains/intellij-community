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
package org.jetbrains.idea.maven.dom;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.idea.maven.dom.plugin.MavenDomParameter;

import java.util.List;

public class MavenPluginModelDocumentationProvider implements DocumentationProvider {
  public String getQuickNavigateInfo(PsiElement element) {
    return getDocForMavenPluginParameter(element);
  }

  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  public String generateDoc(PsiElement element, PsiElement originalElement) {
    return getDocForMavenPluginParameter(element);
  }

  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }

  private String getDocForMavenPluginParameter(PsiElement element) {
    MavenDomParameter p = element.getUserData(MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY);
    if (p == null) return null;
    return p.getDescription().getStringValue();
  }
}
