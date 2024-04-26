// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlLanguageBreadcrumbsInfoProvider implements BreadcrumbsProvider {
  @Override
  public boolean acceptElement(final @NotNull PsiElement e) {
    return e instanceof XmlTag && e.isValid();
  }

  @Override
  public Language[] getLanguages() {
    return new Language[]{XMLLanguage.INSTANCE, XHTMLLanguage.INSTANCE, HTMLLanguage.INSTANCE};
  }

  @Override
  public @NotNull String getElementInfo(final @NotNull PsiElement e) {
    return getInfo(e);
  }

  public static @NotNull String getInfo(@NotNull PsiElement e) {
    final XmlTag tag = (XmlTag)e;
    final boolean addHtmlInfo = e.getContainingFile().getLanguage() != XMLLanguage.INSTANCE;
    return addHtmlInfo ? HtmlUtil.getTagPresentation(tag) : tag.getName();
  }

  @Override
  public @Nullable String getElementTooltip(final @NotNull PsiElement e) {
    return getTooltip((XmlTag)e);
  }

  public static @NotNull String getTooltip(@NotNull XmlTag tag) {
    final StringBuilder result = new StringBuilder("&lt;");
    result.append(tag.getName());
    final XmlAttribute[] attributes = tag.getAttributes();
    for (final XmlAttribute each : attributes) {
      result.append(" ").append(each.getText());
    }

    if (tag.isEmpty()) {
      result.append("/&gt;");
    }
    else {
      result.append("&gt;...&lt;/").append(tag.getName()).append("&gt;");
    }

    return result.toString();
  }
}