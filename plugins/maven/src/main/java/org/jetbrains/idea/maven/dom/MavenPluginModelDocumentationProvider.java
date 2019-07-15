// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;

public class MavenPluginModelDocumentationProvider implements DocumentationProvider {
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return getDocForMavenPluginParameter(element, false);
  }

  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    return getDocForMavenPluginParameter(element, true);
  }

  private String getDocForMavenPluginParameter(PsiElement element, boolean html) {
    MavenPluginConfigurationDomExtender.ParameterData p = element.getUserData(MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY);
    if (p == null) return null;

    String[] ss = html ? new String[]{"<br>", "<b>", "</b>", "<i>", "</i>"}
                       : new String[]{"\n ", "", "", "", ""};

    String text = "";
    if (html) {
      text += "Type: " + ss[1] + p.parameter.getType().getStringValue() + ss[2] + ss[0];
      if (p.defaultValue != null) text += "Default Value: " + ss[1] + p.defaultValue + ss[2] + ss[0];
      if (p.expression != null) text += "Expression: " + ss[1] + p.expression + ss[2] + ss[0];
      if (p.parameter.getRequired().getValue() == Boolean.TRUE) text += ss[1] + "Required" + ss[2] + ss[0];
      text += ss[0];
    }
    text += ss[3] + p.parameter.getDescription().getStringValue() + ss[4];
    return text;
  }
}
