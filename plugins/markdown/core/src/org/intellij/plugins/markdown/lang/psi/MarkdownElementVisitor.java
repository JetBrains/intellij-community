package org.intellij.plugins.markdown.lang.psi;

import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.psi.impl.*;
import org.jetbrains.annotations.NotNull;

public class MarkdownElementVisitor extends PsiElementVisitor {
  public void visitMarkdownFile(@NotNull MarkdownFile file) {
    visitFile(file);
  }

  public void visitLinkDestination(@NotNull MarkdownLinkDestination linkDestination) {
    visitElement(linkDestination);
  }

  public void visitParagraph(@NotNull MarkdownParagraph paragraph) {
    visitElement(paragraph);
  }

  public void visitList(@NotNull MarkdownList list) {
    visitElement(list);
  }

  public void visitTable(@NotNull MarkdownTable table) {
    visitElement(table);
  }

  public void visitBlockQuote(@NotNull MarkdownBlockQuote blockQuote) {
    visitElement(blockQuote);
  }

  public void visitCodeFence(@NotNull MarkdownCodeFence codeFence) {
    visitElement(codeFence);
  }

  public void visitHeader(@NotNull MarkdownHeader header) {
    visitElement(header);
  }
}
