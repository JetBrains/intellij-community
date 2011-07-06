package com.intellij.codeInsight.template.zencoding.filters;

import com.intellij.codeInsight.template.zencoding.nodes.GenerationNode;
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
  public GenerationNode filterNode(@NotNull GenerationNode node) {
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
