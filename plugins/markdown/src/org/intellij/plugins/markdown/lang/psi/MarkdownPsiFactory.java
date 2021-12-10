package org.intellij.plugins.markdown.lang.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.lang.psi.impl.*;
import org.jetbrains.annotations.NotNull;

public enum MarkdownPsiFactory {
  INSTANCE;

  public PsiElement createElement(@NotNull ASTNode node) {
    final IElementType elementType = node.getElementType();

    if (elementType == MarkdownElementTypes.PARAGRAPH) {
      return new MarkdownParagraphImpl(node);
    }
    if (MarkdownTokenTypeSets.HEADERS.contains(elementType)) {
      return new MarkdownHeaderImpl(node);
    }
    if (elementType == MarkdownElementTypes.CODE_FENCE) {
      return ((MarkdownCodeFenceImpl)node);
    }
    if (elementType == MarkdownElementTypes.IMAGE) {
      return new MarkdownImage(node);
    }
    if (MarkdownTokenTypeSets.LISTS.contains(elementType)) {
      return new MarkdownList(node);
    }
    if (elementType == MarkdownElementTypes.LIST_ITEM) {
      return new MarkdownListItem(node);
    }
    if (elementType == MarkdownElementTypes.BLOCK_QUOTE) {
      return new MarkdownBlockQuote(node);
    }
    if (elementType == MarkdownElementTypes.SHORT_REFERENCE_LINK) {
      return new MarkdownShortReferenceLink(node);
    }
    if (elementType == MarkdownElementTypes.LINK_DEFINITION) {
      return new MarkdownLinkDefinition(node);
    }
    if (elementType == MarkdownElementTypes.LINK_DESTINATION) {
      return new MarkdownLinkDestinationImpl(node);
    }
    if (elementType == MarkdownElementTypes.LINK_LABEL) {
      return new MarkdownLinkLabel(node);
    }
    if (elementType == MarkdownElementTypes.CODE_BLOCK) {
      return new MarkdownCodeBlockImpl(node);
    }
    if (elementType == MarkdownElementTypes.TABLE) {
      return new MarkdownTable(node);
    }
    if (elementType == MarkdownElementTypes.TABLE_ROW || elementType == MarkdownElementTypes.TABLE_HEADER) {
      return new MarkdownTableRow(node);
    }
    if (elementType == MarkdownElementTypes.TABLE_CELL) {
      return new MarkdownTableCell(node);
    }

    return new ASTWrapperPsiElement(node);
  }
}
