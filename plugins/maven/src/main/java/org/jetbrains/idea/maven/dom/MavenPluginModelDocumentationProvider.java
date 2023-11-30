// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;

public class MavenPluginModelDocumentationProvider implements DocumentationProvider {
  @Override
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return getDocForMavenPluginParameter(element, false);
  }

  @Override
  public @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
    return getDocForMavenPluginParameter(element, true);
  }

  private static @Nls String getDocForMavenPluginParameter(PsiElement element, boolean html) {
    MavenPluginConfigurationDomExtender.ParameterData p = element.getUserData(MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY);
    if (p == null) return null;

    String[] ss = html ? new String[]{"<br>", "<b>", "</b>", "<i>", "</i>"}
                       : new String[]{"\n ", "", "", "", ""};

    String text = "";
    if (html) {
      text += MavenDomBundle.message("plugin.model.doc.type") + ss[1] + p.parameter.getType().getStringValue() + ss[2] + ss[0];
      if (p.defaultValue != null) text += MavenDomBundle.message("plugin.model.doc.default.value") + ss[1] + p.defaultValue + ss[2] + ss[0];
      if (p.expression != null) text += MavenDomBundle.message("plugin.model.doc.expression") + ss[1] + p.expression + ss[2] + ss[0];
      if (p.parameter.getRequired().getValue() == Boolean.TRUE) text += ss[1] +
                                                                        MavenDomBundle.message("plugin.model.doc.required") + ss[2] + ss[0];
      text += ss[0];
    }
    text += ss[3] + p.parameter.getDescription().getStringValue() + ss[4];
    return text;
  }
}
