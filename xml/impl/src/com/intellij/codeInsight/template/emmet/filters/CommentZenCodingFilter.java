// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommentZenCodingFilter extends ZenCodingFilter {
  private static String buildCommentString(@Nullable String classAttr, @Nullable String idAttr) {
    StringBuilder builder = new StringBuilder();
    if (!Strings.isEmpty(idAttr)) {
      builder.append('#').append(idAttr);
    }
    if (!Strings.isEmpty(classAttr)) {
      builder.append('.').append(classAttr);
    }
    return builder.toString();
  }

  @Override
  public @NotNull String filterText(@NotNull String text, @NotNull TemplateToken token) {
    XmlTag tag = token.getXmlTag();
    if (tag != null) {
      String classAttr = token.getAttributes().get(HtmlUtil.CLASS_ATTRIBUTE_NAME);
      String idAttr = token.getAttributes().get(HtmlUtil.ID_ATTRIBUTE_NAME);
      if (!Strings.isEmpty(classAttr) || !Strings.isEmpty(idAttr)) {
        String commentString = buildCommentString(classAttr, idAttr);
        return String.format(getCommentFormat(), text, commentString);
      }
    }
    return text;
  }

  protected @NotNull String getCommentFormat() {
    return "%s\n<!-- /%s -->";
  }

  @Override
  public @NotNull String getSuffix() {
    return "c";
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    PsiElement parent = context.getParent();
    return parent != null && parent.getLanguage() instanceof XMLLanguage;
  }

  @Override
  public @NotNull String getDisplayName() {
    return XmlBundle.message("emmet.filter.comment.tags");
  }
}
