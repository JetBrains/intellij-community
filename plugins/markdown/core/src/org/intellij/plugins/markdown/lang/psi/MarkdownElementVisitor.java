package org.intellij.plugins.markdown.lang.psi;

import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable;
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
