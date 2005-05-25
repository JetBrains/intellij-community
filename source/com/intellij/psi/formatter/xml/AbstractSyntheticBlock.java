package com.intellij.psi.formatter.xml;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.formatter.common.AbstractBlock;

import java.util.List;


public abstract class AbstractSyntheticBlock implements Block{
  protected final Indent myIndent;
  protected final XmlFormattingPolicy myXmlFormattingPolicy;
  protected final ASTNode myEndTreeNode;
  protected final ASTNode myStartTreeNode;
  private final XmlTag myTag;

  public AbstractSyntheticBlock(List<Block> subBlocks, Block parent, XmlFormattingPolicy policy, Indent indent) {
    myEndTreeNode = ((AbstractXmlBlock)subBlocks.get(subBlocks.size() - 1)).getTreeNode();
    myStartTreeNode = ((AbstractXmlBlock)subBlocks.get(0)).getTreeNode();
    myIndent = indent;
    myXmlFormattingPolicy = policy;
    myTag = ((AbstractXmlBlock)parent).getTag();
  }

  private boolean isEndOfTag() {
    return myEndTreeNode.getElementType() == ElementType.XML_TAG_END;
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

  protected IElementType getTagType() {
    return myXmlFormattingPolicy.getTagType();
  }

  protected int getMaxLine() {
    if (!myXmlFormattingPolicy.getShouldKeepLineBreaks()) return 1;
    return myXmlFormattingPolicy.getKeepBlankLines() + 1;
  }

  protected Formatter getFormatter() {
    return Formatter.getInstance();
  }

  protected boolean isXmlTagName(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_START_TAG_START && type2 == ElementType.XML_NAME) return true;
    if (type1 == ElementType.XML_END_TAG_START && type2 == ElementType.XML_NAME) return true;
    if (type1 == ElementType.XML_NAME && type2 == ElementType.XML_TAG_END) return true;
    if (type1 == ElementType.XML_NAME && type2 == ElementType.XML_EMPTY_ELEMENT_END) return true;
    if (type1 == ElementType.XML_ATTRIBUTE && type2 == ElementType.XML_EMPTY_ELEMENT_END) return true;
    if (type1 == ElementType.XML_ATTRIBUTE && type2 == ElementType.XML_TAG_END) return true;
    return false;
  }

  public boolean endsWithText() {
    return myEndTreeNode.getElementType() == ElementType.XML_TEXT || myEndTreeNode.getElementType() == ElementType.XML_DATA_CHARACTERS;
  }

  public boolean isTagDescription() {
    final ASTNode startTreeNode = myStartTreeNode;
    return isTagDescription(startTreeNode);
  }

  private static boolean isTagDescription(final ASTNode startTreeNode) {
    return startTreeNode.getElementType() == ElementType.XML_START_TAG_START || startTreeNode.getElementType() == ElementType.XML_END_TAG_START;
  }

  public boolean startsWithText() {
    return myStartTreeNode.getElementType() == ElementType.XML_TEXT || myStartTreeNode.getElementType() == ElementType.XML_DATA_CHARACTERS;
  }

  public boolean endsWithTextElement() {
    if (endsWithText()) return true;
    if (isEndOfTag() && myXmlFormattingPolicy.isTextElement(getTag())) return true;
    if (isTextTag(myEndTreeNode)) return true;
    return false;
  }

  protected XmlTag getTag() {
    return myTag;
  }

  public boolean startsWithTextElement() {
    if (startsWithText()) return true;
    if (isStartOfTag() && myXmlFormattingPolicy.isTextElement(getTag())) return true;
    if (isTextTag(myStartTreeNode)) return true;
    return false;
  }

  private boolean isTextTag(final ASTNode treeNode) {
    return treeNode.getElementType() == myXmlFormattingPolicy.getTagType() && myXmlFormattingPolicy.isTextElement((XmlTag)SourceTreeToPsiMap.treeElementToPsi(treeNode));
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
                                                  XmlFormattingPolicy policy) {
    if (!isTagDescription(((AbstractBlock)subBlocks.get(0)).getNode())
        && (policy.getShouldKeepWhiteSpaces()
        || policy.keepWhiteSpacesInsideTag(((XmlTagBlock)parent).getTag()))) {
      return new ReadOnlySyntheticBlock(subBlocks, parent, policy, indent);
    } else {
      return new SyntheticBlock(subBlocks, parent, indent, policy);
    }
  }

  public boolean isIncomplete() {
    return getSubBlocks().get(getSubBlocks().size() - 1).isIncomplete();
  }

  public boolean startsWithTag() {
    return myStartTreeNode.getElementType() == getTagType();
  }

  public boolean endsWithTag() {
    return myEndTreeNode.getElementType() == getTagType();
  }
  
  public boolean isJspTextBlock() {
    final List<Block> subBlocks = getSubBlocks();
    return subBlocks.size() == 1 && subBlocks.get(0) instanceof JspTextBlock;
  }  
}
