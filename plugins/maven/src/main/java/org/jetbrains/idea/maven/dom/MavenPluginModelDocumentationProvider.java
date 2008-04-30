package org.jetbrains.idea.maven.dom;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.idea.maven.dom.plugin.Parameter;

public class MavenPluginModelDocumentationProvider implements DocumentationProvider {
  public String getQuickNavigateInfo(PsiElement element) {
    return getDocForMavenPluginParameter(element);
  }

  public String getUrlFor(PsiElement element, PsiElement originalElement) {
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
    Parameter p = element.getUserData(MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY);
    if (p == null) return null;
    return p.getDescription().getStringValue();
  }
}
