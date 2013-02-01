package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class TrimZenCodingFilter extends ZenCodingFilter {
  private static final Pattern PATTERN = Pattern.compile("^([\\s|\u00a0])?[\\d|#|\\-|\\*|\u2022]+\\.?\\s*");

  @NotNull
  @Override
  public String getSuffix() {
    return "t";
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }

  @NotNull
  @Override
  public String filterText(@NotNull String text, @NotNull TemplateToken token) {
    if(token == TemplateToken.EMPTY_TEMPLATE_TOKEN) {
      return PATTERN.matcher(text).replaceAll("");
    }
    return text;
  }

  @NotNull
  @Override
  public GenerationNode filterNode(@NotNull GenerationNode node) {
    doFilter(node);
    return node;
  }

  private static void doFilter(GenerationNode node) {
    final String surroundedText = node.getSurroundedText();
    if (surroundedText != null) {
      node.setSurroundedText(PATTERN.matcher(surroundedText).replaceAll(""));
    } else if(node.getTemplateToken() == TemplateToken.EMPTY_TEMPLATE_TOKEN) {
    }
    for (GenerationNode child : node.getChildren()) {
      doFilter(child);
    }
  }
}
