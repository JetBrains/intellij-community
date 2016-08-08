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
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageViewTypeLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenModelDocumentationProvider implements DocumentationProvider, ElementDescriptionProvider {
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return getDoc(element, false);
  }

  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    element = getMavenElement(element);
    if (element == null) return null;
    if (MavenDomUtil.isMavenProperty(element)) return Collections.emptyList();

    // todo hard-coded maven version
    // todo add auto-opening the element's doc
    //String name = ((PsiNamedElement)element).getName();
    return Collections.singletonList("http://maven.apache.org/ref/2.2.1/maven-model/maven.html");
  }

  public String generateDoc(PsiElement element, PsiElement originalElement) {
    return getDoc(element, true);
  }

  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }

  @Nullable
  private static String getDoc(PsiElement element, boolean html) {
    return getMavenElementDescription(element, DescKind.TYPE_NAME_VALUE, html);
  }

  public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    return getMavenElementDescription(element, location instanceof UsageViewTypeLocation ? DescKind.TYPE : DescKind.NAME, false);
  }

  @Nullable
  private static String getMavenElementDescription(PsiElement e, DescKind kind, boolean html) {
    e = getMavenElement(e);
    if (e == null) return null;

    if (e instanceof FakePsiElement) {
      return ((FakePsiElement)e).getPresentableText();
    }

    boolean property = MavenDomUtil.isMavenProperty(e);

    String type = property ? "Property" : "Model Property";
    if (kind == DescKind.TYPE) return type;

    String name = buildPropertyName(e, property);
    if (kind == DescKind.NAME) return name;

    if (kind == DescKind.TYPE_NAME_VALUE) {
      String br = html ? "<br>" : "\n ";
      String[] bold = html ? new String[]{"<b>", "</b>"} : new String[]{"", ""};
      String valueSuffix = "";
      if (e instanceof XmlTag) {
        valueSuffix = ": " + bold[0] + ((XmlTag)e).getValue().getTrimmedText() + bold[1];
      }
      return type + br + name + valueSuffix;
    }

    MavenLog.LOG.error("unexpected desc kind: " + kind);
    return null;
  }

  private static String buildPropertyName(PsiElement e, boolean property) {
    if (property) return DescriptiveNameUtil.getDescriptiveName(e);

    List<String> path = new ArrayList<>();
    do {
      path.add(DescriptiveNameUtil.getDescriptiveName(e));
    }
    while ((e = PsiTreeUtil.getParentOfType(e, XmlTag.class)) != null);
    Collections.reverse(path);
    return StringUtil.join(path, ".");
  }

  private static PsiElement getMavenElement(PsiElement e) {
    if (e instanceof MavenPsiElementWrapper) e = ((MavenPsiElementWrapper)e).getWrappee();

    if (!MavenDomUtil.isMavenFile(e)) return null;
    if (e instanceof PsiFile) return null;
    return e;
  }

  private enum DescKind {
    TYPE, NAME, TYPE_NAME_VALUE
  }
}
