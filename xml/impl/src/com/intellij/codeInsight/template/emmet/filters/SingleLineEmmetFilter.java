// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

public class SingleLineEmmetFilter extends ZenCodingFilter {
  @Override
  public @NotNull String getSuffix() {
    return "s";
  }

  @Override
  public @NotNull String filterText(@NotNull String text, @NotNull TemplateToken token) {
    return StringUtil.replace(text, "\n", "");
  }

  @Override
  public @NotNull GenerationNode filterNode(@NotNull GenerationNode node) {
    TemplateImpl template = node.getTemplateToken().getTemplate();
    if (template != null) {
      template.setToReformat(false);
    }
    for (GenerationNode generationNode : node.getChildren()) {
      filterNode(generationNode);
    }
    return node;
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }

  @Override
  public @NotNull String getDisplayName() {
    return XmlBundle.message("emmet.filter.single.line");
  }
}
