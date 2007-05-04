package com.intellij.psi.formatter.xml;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

import java.util.List;


public abstract class AbstractSyntheticBlock implements Block{
  protected final Indent myIndent;
  protected final XmlFormattingPolicy myXmlFormattingPolicy;
  protected final ASTNode myEndTreeNode;
  protected final ASTNode myStartTreeNode;
  private final XmlTag myTag;

  public AbstractSyntheticBlock(List<Block> subBlocks, Block parent, XmlFormattingPolicy policy, Indent indent) {
    myEndTreeNode = getLastNode(subBlocks);
    myStartTreeNode = getFirstNode(subBlocks);
    myIndent = indent;
    myXmlFormattingPolicy = policy;
    myTag = ((AbstractXmlBlock)parent).getTag();
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.xml.AbstractSyntheticBlock");

  private ASTNode getFirstNode(final List<Block> subBlocks) {
    LOG.assertTrue(!subBlocks.isEmpty());
    final Block firstBlock = subBlocks.get(0);
    if (firstBlock instanceof AbstractBlock) {
      return ((AbstractBlock)firstBlock).getTreeNode();
    } else {
      return getFirstNode(firstBlock.getSubBlocks());
    }
  }

  private ASTNode getLastNode(final List<Block> subBlocks) {
    LOG.assertTrue(!subBlocks.isEmpty());
    final Block lastBlock = subBlocks.get(subBlocks.size() - 1);
    if (lastBlock instanceof AbstractBlock) {
      return ((AbstractBlock)lastBlock).getTreeNode();
    } else {
      return getLastNode(lastBlock.getSubBlocks());
    }
  }

  private boolean isEndOfTag() {
    return myEndTreeNode.getElementType() == XmlElementType.XML_TAG_END;
  }

  public Wrap getWrap() {
    return null;
  }

  public Indent getIndent() {
    return myIndent;
  }

  public Alignment getAlignment() {
    return null;
  }

  protected static boolean isXmlTagName(final IElementType type1, final IElementType type2) {
    if (type1 == XmlElementType.XML_START_TAG_START && type2 == XmlElementType.XML_NAME) return true;
    if (type1 == XmlElementType.XML_END_TAG_START && type2 == XmlElementType.XML_NAME) return true;
    if (type1 == XmlElementType.XML_NAME && type2 == XmlElementType.XML_TAG_END) return true;
    if (type1 == XmlElementType.XML_NAME && type2 == XmlElementType.XML_EMPTY_ELEMENT_END) return true;
    if (type1 == XmlElementType.XML_ATTRIBUTE && type2 == XmlElementType.XML_EMPTY_ELEMENT_END) return true;
    return type1 == XmlElementType.XML_ATTRIBUTE && type2 == XmlElementType.XML_TAG_END;
  }

  public boolean endsWithText() {
    return myEndTreeNode.getElementType() == XmlElementType.XML_TEXT || myEndTreeNode.getElementType() == XmlElementType.XML_DATA_CHARACTERS;
  }

  public boolean isTagDescription() {
    final ASTNode startTreeNode = myStartTreeNode;
    return isTagDescription(startTreeNode);
  }

  private static boolean isTagDescription(final ASTNode startTreeNode) {
    return startTreeNode.getElementType() == XmlElementType.XML_START_TAG_START || startTreeNode.getElementType() == XmlElementType.XML_END_TAG_START;
  }

  public boolean startsWithText() {
    return myStartTreeNode.getElementType() == XmlElementType.XML_TEXT || myStartTreeNode.getElementType() == XmlElementType.XML_DATA_CHARACTERS;
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

  protected static TextRange calculateTextRange(final List<Block> subBlocks) {
    return new TextRange(subBlocks.get(0).getTextRange().getStartOffset(),
                                subBlocks.get(subBlocks.size()-  1).getTextRange().getEndOffset());
  }

  public static Block createSynteticBlock(final List<Block> subBlocks,
                                          final Block parent,
                                          final Indent indent,
                                          XmlFormattingPolicy policy, final Indent childIndent) {
    return new SyntheticBlock(subBlocks, parent, indent, policy, childIndent);
  }

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
    final List<Block> subBlocks = getSubBlocks();
    return subBlocks.size() == 1 && subBlocks.get(0) instanceof JspTextBlock &&
      ((JspTextBlock) subBlocks.get(0)).getNode() instanceof OuterLanguageElement;
  }

  public boolean isJspxTextBlock() {
    final List<Block> subBlocks = getSubBlocks();
    return subBlocks.size() == 1 && subBlocks.get(0) instanceof JspTextBlock &&
      ((JspTextBlock) subBlocks.get(0)).getNode() instanceof XmlText;
  }

  public boolean isLeaf() {
    return false;
  }

  public boolean startsWithCDATA() {
    return isCDATA(myStartTreeNode.getFirstChildNode());
  }

  private boolean isCDATA(final ASTNode node) {
    return node != null && node.getElementType() == XmlElementType.XML_CDATA;
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

}
