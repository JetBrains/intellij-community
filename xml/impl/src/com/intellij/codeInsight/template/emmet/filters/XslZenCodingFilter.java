// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.XslTextContextType;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGeneratorImpl;
import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XslZenCodingFilter extends ZenCodingFilter {
  private final XmlZenCodingGeneratorImpl myDelegate = new XmlZenCodingGeneratorImpl();
  private static final @NonNls String SELECT_ATTR_NAME = "select";

  @Override
  public @NotNull GenerationNode filterNode(final @NotNull GenerationNode node) {
    TemplateToken token = node.getTemplateToken();
    final XmlTag tag = token != null ? token.getXmlTag() : null;
    if (tag != null) {
      if (token.getAttributes().containsKey(SELECT_ATTR_NAME)) {
        return node;
      }
      if (isOurTag(tag, !node.getChildren().isEmpty())) {
        XmlAttribute attribute = tag.getAttribute(SELECT_ATTR_NAME);
        if (attribute != null) {
          //fake file processing, no write-action needed
          attribute.delete();
        }
      }
      return node;
    }
    return node;
  }

  private static boolean isOurTag(XmlTag tag, boolean hasChildren) {
    if (hasChildren) {
      String name = tag.getLocalName();
      return name.equals("with-param") || name.equals("variable");
    }
    return false;
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return myDelegate.isMyContext(context, true) ||
           myDelegate.isMyContext(context, false);
  }

  @Override
  public @NotNull String getSuffix() {
    return "xsl";
  }

  @Override
  public boolean isAppliedByDefault(@NotNull PsiElement context) {
    return XslTextContextType.isXslOrXsltFile(context.getContainingFile()) || super.isAppliedByDefault(context);
  }

  @Override
  public @NotNull String getDisplayName() {
    return XmlBundle.message("emmet.filter.xsl.tuning");
  }
}
