package com.intellij.psi.formatter.newXmlFormatter;

import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.lang.ASTNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SynteticBlock implements Block{
  private final List<Block> mySubBlocks;
  private final Block myParent;
  private final TextRange myTextRange;
  private final Indent myIndent;
  private final XmlFormattingPolicy myXmlFormattingPolicy;
  private static final ArrayList<Block> EMPTY = new ArrayList<Block>();
  private final XmlTag myTag;

  private final ASTNode myEndTreeNode;
  private final ASTNode myStartTreeNode;

  public SynteticBlock(final List<Block> subBlocks, final Block parent, final Indent indent, XmlFormattingPolicy policy) {
    myEndTreeNode = ((AbstractXmlBlock)subBlocks.get(subBlocks.size() - 1)).getTreeNode();
    myStartTreeNode = ((AbstractXmlBlock)subBlocks.get(0)).getTreeNode();
    myParent = parent;
    myTag = ((AbstractXmlBlock)myParent).getTag();
    if (!isTagDescription() && (policy.getShouldKeepWhiteSpaces() || policy.keepWhiteSpacesInsideTag(myTag))) {
      mySubBlocks = EMPTY;
    } else {
      mySubBlocks = subBlocks;
    }

    myIndent = indent;
    myXmlFormattingPolicy = policy;
    myTextRange = new TextRange(subBlocks.get(0).getTextRange().getStartOffset(),
                                subBlocks.get(subBlocks.size()-  1).getTextRange().getEndOffset());

    for (Iterator<Block> iterator = subBlocks.iterator(); iterator.hasNext();) {
      AbstractXmlBlock abstractXmlBlock = (AbstractXmlBlock)iterator.next();
      abstractXmlBlock.setParent(this);
    }
  }

  private boolean isEndOfTag() {
    return myEndTreeNode.getElementType() == ElementType.XML_TAG_END;
  }

  public TextRange getTextRange() {
    return myTextRange;
  }

  public List<Block> getSubBlocks() {
    return mySubBlocks;
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

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    final IElementType type1 = ((AbstractXmlBlock)child1).myNode.getElementType();
    final IElementType type2 = ((AbstractXmlBlock)child2).myNode.getElementType();

    if (isXmlTagName(type1, type2)){
      final int spaces = myXmlFormattingPolicy.getShouldAddSpaceAroundTagName() ? 1 : 0;
      return getFormatter().createSpaceProperty(spaces, spaces, 0, getMaxLine());
    } else if (type2 == ElementType.XML_ATTRIBUTE) {
      return getFormatter().createSpaceProperty(1, 1, 0, getMaxLine());
    } else if (((AbstractXmlBlock)child1).isTextElement() && ((AbstractXmlBlock)child2).isTextElement()) {
      return getFormatter().createSafeSpace();
    } else if (type2 == getTagType() && (type1 == ElementType.XML_TEXT || type1== getTagType())
               && ((AbstractXmlBlock)child2).insertLineBreakBeforeTag()) {
      //<tag/>text <tag/></tag>
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 2, getMaxLine());
    } else if (type2 == getTagType() && (type1 == ElementType.XML_TEXT || type1== getTagType())
               && ((AbstractXmlBlock)child2).removeLineBreakBeforeTag()) {
      //<tag/></tag> text</tag>
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, 0);
    }

    if (type1 == getTagType() && type2 == ElementType.XML_TEXT) {     //<tag/>-text
      return getFormatter().createSpaceProperty(0, 0, 1, getMaxLine());
    }
    return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, getMaxLine());
  }

  private IElementType getTagType() {
    return myXmlFormattingPolicy.getTagType();
  }

  protected int getMaxLine() {
    if (!myXmlFormattingPolicy.getShouldKeepLineBreaks()) return 1;
    return myXmlFormattingPolicy.getKeepBlankLines() + 1;
  }

  private Formatter getFormatter() {
    return Formatter.getInstance();
  }

  private boolean isXmlTagName(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_START_TAG_START && type2 == ElementType.XML_NAME) return true;
    if (type1 == ElementType.XML_END_TAG_START && type2 == ElementType.XML_NAME) return true;
    if (type1 == ElementType.XML_NAME && type2 == ElementType.XML_TAG_END) return true;
    if (type1 == ElementType.XML_NAME && type2 == ElementType.XML_EMPTY_ELEMENT_END) return true;
    if (type1 == ElementType.XML_ATTRIBUTE && type2 == ElementType.XML_EMPTY_ELEMENT_END) return true;
    if (type1 == ElementType.XML_ATTRIBUTE && type2 == ElementType.XML_TAG_END) return true;
    return false;
  }

  public Block getParent() {
    return myParent;
  }

  public boolean endsWithText() {
    return myEndTreeNode.getElementType() == ElementType.XML_TEXT || myEndTreeNode.getElementType() == ElementType.XML_DATA_CHARACTERS;
  }

  public boolean isTagDescription() {
    return myStartTreeNode.getElementType() == ElementType.XML_START_TAG_START || myStartTreeNode.getElementType() == ElementType.XML_END_TAG_START;
  }

  public boolean startsWithText() {
    return myStartTreeNode.getElementType() == ElementType.XML_TEXT || myStartTreeNode.getElementType() == ElementType.XML_DATA_CHARACTERS;
  }

  public boolean endsWithTextElement() {
    if (endsWithText()) return true;
    if (isEndOfTag() && myXmlFormattingPolicy.isTextElement(myTag)) return true;
    if (isTextTag(myEndTreeNode)) return true;
    return false;
  }

  public boolean startsWithTextElement() {
    if (startsWithText()) return true;
    if (isStartOfTag() && myXmlFormattingPolicy.isTextElement(myTag)) return true;
    if (isTextTag(myStartTreeNode)) return true;
    return false;
  }

  private boolean isTextTag(final ASTNode treeNode) {
    return treeNode.getElementType() == myXmlFormattingPolicy.getTagType() && myXmlFormattingPolicy.isTextElement((XmlTag)SourceTreeToPsiMap.treeElementToPsi(treeNode));
  }

  private boolean isStartOfTag() {
    return myStartTreeNode.getElementType() == ElementType.XML_START_TAG_START || myStartTreeNode.getElementType() == ElementType.XML_END_TAG_START;
  }
}
