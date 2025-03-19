// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageViewTypeLocation;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MavenModelDocumentationProvider implements DocumentationProvider, ElementDescriptionProvider {
  @Override
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return getDoc(element, false);
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    element = getMavenElement(element);
    if (element == null) return null;
    if (MavenDomUtil.isMavenProperty(element)) return Collections.emptyList();

    // todo hard-coded maven version
    // todo add auto-opening the element's doc
    //String name = ((PsiNamedElement)element).getName();
    return Collections.singletonList("http://maven.apache.org/ref/2.2.1/maven-model/maven.html");
  }

  @Override
  public @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
    return getDoc(element, true);
  }

  private static @Nullable @Nls String getDoc(PsiElement element, boolean html) {
    return getMavenElementDescription(element, DescKind.TYPE_NAME_VALUE, html);
  }

  @Override
  public @Nls String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    return getMavenElementDescription(element, location instanceof UsageViewTypeLocation ? DescKind.TYPE : DescKind.NAME, false);
  }

  private static @Nullable @NlsContexts.DetailedDescription String getMavenElementDescription(PsiElement e, DescKind kind, boolean html) {
    e = getMavenElement(e);
    if (e == null) return null;

    if (e instanceof FakePsiElement) {
      return ((FakePsiElement)e).getPresentableText();
    }

    boolean property = MavenDomUtil.isMavenProperty(e);

    String type = property ? MavenDomBundle.message("text.property") : MavenDomBundle.message("text.model.property");
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

  private static @NlsContexts.DetailedDescription String buildPropertyName(PsiElement e, boolean property) {
    if (property) return DescriptiveNameUtil.getDescriptiveName(e); //NON-NLS - suprress warning

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
