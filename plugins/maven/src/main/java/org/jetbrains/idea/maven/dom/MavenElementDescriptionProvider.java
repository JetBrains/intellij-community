package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenElementDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    if (!MavenDomUtil.isMavenFile(element)) return null;

    boolean property = MavenDomUtil.isMavenProperty(element);

    if (location instanceof UsageViewTypeLocation) {
      return property ? "Property" : "Model Property";
    }

    if (property) return UsageViewUtil.getDescriptiveName(element);

    List<String> path = new ArrayList<String>();
    do {
      path.add(UsageViewUtil.getDescriptiveName(element));
    }
    while ((element = PsiTreeUtil.getParentOfType(element, XmlTag.class)) != null);
    Collections.reverse(path);
    return StringUtil.join(path, ".");
  }
}
