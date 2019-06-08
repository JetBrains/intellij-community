package org.intellij.plugins.markdown.lang.parser;

import com.intellij.lang.PsiBuilder;
import org.intellij.markdown.IElementType;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.ast.LeafASTNode;
import org.intellij.markdown.ast.visitors.RecursiveVisitor;
import org.intellij.plugins.markdown.lang.MarkdownElementType;
import org.jetbrains.annotations.NotNull;

public class PsiBuilderFillingVisitor extends RecursiveVisitor {
  @NotNull
  private final PsiBuilder builder;

  public PsiBuilderFillingVisitor(@NotNull PsiBuilder builder) {
    this.builder = builder;
  }

  @Override
  public void visitNode(@NotNull ASTNode node) {
    if (node instanceof LeafASTNode) {
      /* a hack for the link reference definitions:
       * they are being parsed independent from link references and
       * the link titles and urls are tokens instead of composite elements
       */
      final IElementType type = node.getType();
      if (type != MarkdownElementTypes.LINK_LABEL && type != MarkdownElementTypes.LINK_DESTINATION) {
        return;
      }
    }

    ensureBuilderInPosition(node.getStartOffset());
    final PsiBuilder.Marker marker = builder.mark();

    super.visitNode(node);

    ensureBuilderInPosition(node.getEndOffset());
    marker.done(MarkdownElementType.platformType(node.getType()));
  }

  private void ensureBuilderInPosition(int position) {
    while (builder.getCurrentOffset() < position) {
      builder.advanceLexer();
    }

    if (builder.getCurrentOffset() != position) {
      throw new AssertionError("parsed tree and lexer are unsynchronized");
    }
  }
}
