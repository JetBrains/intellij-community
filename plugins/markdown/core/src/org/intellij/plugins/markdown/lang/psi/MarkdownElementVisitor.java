package org.intellij.plugins.markdown.lang.psi;

import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.psi.impl.*;
import org.jetbrains.annotations.NotNull;

public class MarkdownElementVisitor extends PsiElementVisitor {
  public void visitMarkdownFile(@NotNull MarkdownFile file) {
    visitFile(file);
  }

  /**
   * @deprecated Please use {@link MarkdownLinkDestination} and
   * {@link MarkdownElementVisitor#visitLinkDestination(MarkdownLinkDestination)} instead.
   */
  @Deprecated
  public void visitLinkDestination(@NotNull MarkdownLinkDestinationImpl linkDestination) {
    visitElement(linkDestination);
  }

  public void visitLinkDestination(@NotNull MarkdownLinkDestination linkDestination) {
    //noinspection deprecation
    visitLinkDestination((MarkdownLinkDestinationImpl)linkDestination);
  }

  /**
   * @deprecated Please use {@link MarkdownParagraph} and {@link MarkdownElementVisitor#visitParagraph(MarkdownParagraph)} instead.
   */
  @Deprecated
  public void visitParagraph(@NotNull MarkdownParagraphImpl paragraph) {
    visitElement(paragraph);
  }

  public void visitParagraph(@NotNull MarkdownParagraph paragraph) {
    //noinspection deprecation
    visitParagraph((MarkdownParagraphImpl)paragraph);
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
    //noinspection deprecation
    visitCodeFence((MarkdownCodeFenceImpl)codeFence);
  }

  /**
   * @deprecated Please use {@link MarkdownCodeFence} and {@link MarkdownElementVisitor#visitCodeFence(MarkdownCodeFence)} instead.
   */
  @Deprecated
  public void visitCodeFence(@NotNull MarkdownCodeFenceImpl codeFence) {
    visitElement(codeFence);
  }

  /**
   * @deprecated Please use {@link MarkdownHeader} and {@link MarkdownElementVisitor#visitHeader(MarkdownHeader)} instead.
   */
  @Deprecated
  public void visitHeader(@NotNull MarkdownHeaderImpl header) {
    visitElement(header);
  }

  public void visitHeader(@NotNull MarkdownHeader header) {
    //noinspection deprecation
    visitHeader((MarkdownHeaderImpl)header);
  }
}
