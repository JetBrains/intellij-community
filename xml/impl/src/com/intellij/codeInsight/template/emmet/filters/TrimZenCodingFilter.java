// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrimZenCodingFilter extends ZenCodingFilter {
  private static final Pattern PATTERN = Pattern.compile("^([\\s|\u00a0])?[\\d|#|\\-|\\*|\u2022]+\\.?\\s*");

  @Override
  public @NotNull String getSuffix() {
    return "t";
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }

  @Override
  public @NotNull String getDisplayName() {
    return XmlBundle.message("emmet.filter.trim.line.markers");
  }

  @Override
  public @NotNull String filterText(@NotNull String text, @NotNull TemplateToken token) {
    XmlTag tag = token.getXmlTag();
    if (tag != null && !tag.getText().isEmpty()) {
      tag.accept(new XmlElementVisitor() {
        @Override
        public void visitXmlTag(final @NotNull XmlTag tag) {
          if (!tag.isEmpty()) {
            final XmlTagValue tagValue = tag.getValue();
            final Matcher matcher = PATTERN.matcher(tagValue.getText());
            if (matcher.matches()) {
              tagValue.setText(matcher.replaceAll(""));
            }
          }
          tag.acceptChildren(this);
        }
      });
      return tag.getText();
    }
    else {
      return PATTERN.matcher(token.getTemplateText()).replaceAll("");
    }
  }

  @Override
  public @NotNull GenerationNode filterNode(@NotNull GenerationNode node) {
    doFilter(node);
    return node;
  }

  private static void doFilter(GenerationNode node) {
    final String surroundedText = node.getSurroundedText();
    if (surroundedText != null) {
      node.setSurroundedText(PATTERN.matcher(surroundedText).replaceAll(""));
    }
    for (GenerationNode child : node.getChildren()) {
      doFilter(child);
    }
  }
}
