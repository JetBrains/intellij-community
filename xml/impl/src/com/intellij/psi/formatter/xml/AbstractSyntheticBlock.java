// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.xml;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.IXmlAttributeElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractSyntheticBlock implements Block {
  protected final Indent myIndent;
  protected @NotNull XmlFormattingPolicy myXmlFormattingPolicy;
  protected final ASTNode myEndTreeNode;
  protected final ASTNode myStartTreeNode;
  private final XmlTag myTag;

  public AbstractSyntheticBlock(@NotNull List<@NotNull Block> subBlocks,
                                @NotNull Block parent,
                                @NotNull XmlFormattingPolicy policy,
                                @Nullable Indent indent) {
    myEndTreeNode = getLastNode(subBlocks);
    myStartTreeNode = getFirstNode(subBlocks);
    myIndent = indent;
    myXmlFormattingPolicy = policy;
    if (parent instanceof AbstractXmlBlock) {
      myTag = ((AbstractXmlBlock)parent).getTag();
    }
    else if (parent instanceof AbstractSyntheticBlock) {
      myTag = ((AbstractSyntheticBlock)parent).getTag();
    }
    else {
      throw new IllegalStateException("Parent should be AbstractXmlBlock or AbstractSyntheticBlock, but it is " + parent.getClass());
    }
  }

  private static final Logger LOG = Logger.getInstance(AbstractSyntheticBlock.class);

  public boolean shouldKeepWhiteSpacesInside() {
    return myTag != null && myXmlFormattingPolicy.keepWhiteSpacesInsideTag(myTag);
  }

  private ASTNode getFirstNode(final List<? extends Block> subBlocks) {
    LOG.assertTrue(!subBlocks.isEmpty());
    final Block firstBlock = subBlocks.get(0);
    if (firstBlock instanceof AbstractBlock) {
      return ((AbstractBlock)firstBlock).getNode();
    }
    else {
      return getFirstNode(firstBlock.getSubBlocks());
    }
  }

  private ASTNode getLastNode(final List<? extends Block> subBlocks) {
    LOG.assertTrue(!subBlocks.isEmpty());
    final Block lastBlock = subBlocks.get(subBlocks.size() - 1);
    if (lastBlock instanceof AbstractBlock) {
      return ((AbstractBlock)lastBlock).getNode();
    }
    else {
      return getLastNode(lastBlock.getSubBlocks());
    }
  }

  private boolean isEndOfTag() {
    return myEndTreeNode.getElementType() == XmlTokenType.XML_TAG_END;
  }

  @Override
  public @Nullable Wrap getWrap() {
    return null;
  }

  @Override
  public @Nullable Indent getIndent() {
    return myIndent;
  }

  @Override
  public @Nullable Alignment getAlignment() {
    return null;
  }

  protected boolean isXmlTagName(final IElementType type1, final IElementType type2) {
    if ((type1 == XmlTokenType.XML_NAME || type1 == XmlTokenType.XML_TAG_NAME) && (type2 == XmlTokenType.XML_TAG_END)) return true;
    if ((type1 == XmlTokenType.XML_NAME || type1 == XmlTokenType.XML_TAG_NAME) && (type2 == XmlTokenType.XML_EMPTY_ELEMENT_END)) {
      return true;
    }
    if (isAttributeElementType(type1) && type2 == XmlTokenType.XML_EMPTY_ELEMENT_END) return true;
    return isAttributeElementType(type1) && type2 == XmlTokenType.XML_TAG_END;
  }

  protected boolean isTextNode(IElementType nodeType) {
    return nodeType == XmlElementType.XML_TEXT ||
           nodeType == XmlElementType.HTML_RAW_TEXT;
  }

  public boolean endsWithText() {
    return isTextNode(myEndTreeNode.getElementType()) ||
           myEndTreeNode.getElementType() == XmlTokenType.XML_DATA_CHARACTERS ||
           myEndTreeNode.getElementType() == XmlTokenType.XML_CHAR_ENTITY_REF ||
           myEndTreeNode.getElementType() == XmlElementType.XML_ENTITY_REF;
  }

  public boolean isTagDescription() {
    return isTagDescription(myStartTreeNode);
  }

  private static boolean isTagDescription(final ASTNode startTreeNode) {
    return startTreeNode.getElementType() == XmlTokenType.XML_START_TAG_START ||
           startTreeNode.getElementType() == XmlTokenType.XML_END_TAG_START;
  }

  public boolean startsWithText() {
    return isTextNode(myStartTreeNode.getElementType()) ||
           myStartTreeNode.getElementType() == XmlTokenType.XML_DATA_CHARACTERS ||
           myStartTreeNode.getElementType() == XmlTokenType.XML_CHAR_ENTITY_REF ||
           myStartTreeNode.getElementType() == XmlElementType.XML_ENTITY_REF;
  }

  public boolean endsWithTextElement() {
    if (endsWithText()) return true;
    if (isEndOfTag() && myXmlFormattingPolicy.isTextElement(getTag())) return true;
    return isTextTag(myEndTreeNode);
  }

  protected XmlTag getTag() {
    return myTag;
  }

  public boolean startsWithTextElement() {
    if (startsWithText()) return true;
    if (isStartOfTag() && myXmlFormattingPolicy.isTextElement(getTag())) return true;
    return isTextTag(myStartTreeNode);
  }

  private boolean isTextTag(final ASTNode treeNode) {
    return isXmlTag(treeNode) && myXmlFormattingPolicy.isTextElement((XmlTag)SourceTreeToPsiMap.treeElementToPsi(treeNode));
  }

  private boolean isXmlTag(final ASTNode treeNode) {
    return (treeNode.getPsi() instanceof XmlTag);
  }

  private boolean isStartOfTag() {
    return isTagDescription(myStartTreeNode);
  }

  protected static TextRange calculateTextRange(final List<? extends Block> subBlocks) {
    return new TextRange(subBlocks.get(0).getTextRange().getStartOffset(),
                         subBlocks.get(subBlocks.size() - 1).getTextRange().getEndOffset());
  }

  @Override
  public boolean isIncomplete() {
    return getSubBlocks().get(getSubBlocks().size() - 1).isIncomplete();
  }

  public boolean startsWithTag() {
    return isXmlTag(myStartTreeNode);
  }

  public XmlTag getStartTag() {
    return (XmlTag)myStartTreeNode.getPsi();
  }


  public boolean endsWithTag() {
    return isXmlTag(myEndTreeNode);
  }

  public boolean isJspTextBlock() {
    return false;
  }

  public boolean isJspxTextBlock() {
    return false;
  }

  /**
   * Checks if the block contains a single node which belongs to the outer (template) language.
   *
   * @return True if it does, False otherwise.
   */
  public boolean isOuterLanguageBlock() {
    return (myStartTreeNode == myEndTreeNode) && (myStartTreeNode instanceof OuterLanguageElement);
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  public boolean startsWithCDATA() {
    return isCDATA(myStartTreeNode.getFirstChildNode());
  }

  private boolean isCDATA(final ASTNode node) {
    return node != null && node.getElementType() == XmlElementType.XML_CDATA;
  }

  public boolean containsCDATA() {
    return myStartTreeNode.getElementType() == XmlTokenType.XML_CDATA_START &&
           myEndTreeNode.getElementType() == XmlTokenType.XML_CDATA_END;
  }

  public boolean endsWithCDATA() {
    return isCDATA(myStartTreeNode.getLastChildNode());
  }

  public boolean insertLineFeedAfter() {
    final List<Block> subBlocks = getSubBlocks();
    final Block lastBlock = subBlocks.get(subBlocks.size() - 1);
    if (lastBlock instanceof XmlTagBlock) {
      return insertLineFeedAfter(((XmlTagBlock)lastBlock).getTag());
    }
    if (endsWithText()) {
      return insertLineFeedAfter(myTag);
    }
    return false;
  }

  protected boolean insertLineFeedAfter(final XmlTag tag) {
    return myXmlFormattingPolicy.getWrappingTypeForTagBegin(tag) == WrapType.ALWAYS;
  }

  protected boolean isAttributeElementType(final IElementType elementType) {
    return elementType instanceof IXmlAttributeElementType;
  }
}
