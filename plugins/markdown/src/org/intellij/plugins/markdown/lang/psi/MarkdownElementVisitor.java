package org.intellij.plugins.markdown.lang.psi;

import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.psi.impl.*;
import org.jetbrains.annotations.NotNull;

public class MarkdownElementVisitor extends PsiElementVisitor {
  public void visitMarkdownFile(@NotNull MarkdownFile file) {
    visitFile(file);
  }

  public void visitLinkDestination(@NotNull MarkdownLinkDestinationImpl linkDestination) {
    visitElement(linkDestination);
  }

  public void visitParagraph(@NotNull MarkdownParagraphImpl paragraph) {
    visitElement(paragraph);
  }

  public void visitList(@NotNull MarkdownListImpl list) {
    visitElement(list);
  }

  public void visitTable(@NotNull MarkdownTableImpl table) {
    visitElement(table);
  }

  public void visitBlockQuote(@NotNull MarkdownBlockQuoteImpl blockQuote) {
    visitElement(blockQuote);
  }

  public void visitCodeFence(@NotNull MarkdownCodeFenceImpl codeFence) {
    visitElement(codeFence);
  }

  public void visitHeader(@NotNull MarkdownHeaderImpl header) {
    visitElement(header);
  }
}
