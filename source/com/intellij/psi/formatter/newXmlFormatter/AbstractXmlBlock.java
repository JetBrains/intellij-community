package com.intellij.psi.formatter.newXmlFormatter;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;

import java.util.List;


public abstract class AbstractXmlBlock implements Block {
  protected final ASTNode myNode;
  private List<Block> mySubBlocks;
  private final Wrap myWrap;
  private final Alignment myAlignment;
  private Block myParent;
  protected final XmlFormattingPolicy myXmlFormattingPolicy;

  public AbstractXmlBlock(final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          AbstractXmlBlock parent,
                          final XmlFormattingPolicy policy) {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
    myParent = parent;
    myXmlFormattingPolicy = policy;
  }


  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = buildChildren();
    }
    return mySubBlocks;
  }

  protected abstract List<Block> buildChildren();

  protected boolean containsWhiteSpacesOnly(final ASTNode node) {
    if (node.getElementType() == ElementType.WHITE_SPACE) return true;
    if (node instanceof LeafElement) return false;
    ChameleonTransforming.transformChildren(node);
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child)) return false;
      child = child.getTreeNext();
    }
    return true;
  }

  protected int getWrapType(final int type) {
    if (type == CodeStyleSettings.DO_NOT_WRAP) return Wrap.NONE;
    if (type == CodeStyleSettings.WRAP_ALWAYS) return Wrap.ALWAYS;
    if (type == CodeStyleSettings.WRAP_AS_NEEDED) return Wrap.NORMAL;
    return Wrap.CHOP_DOWN_IF_LONG;
  }

  protected Alignment chooseAlignment(final ASTNode child, final Alignment attrAlignment, final Alignment textAlignment) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return getAlignment();
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE && myXmlFormattingPolicy.getShouldAlignAttributes()) return attrAlignment;
    if (elementType == ElementType.XML_TEXT && myXmlFormattingPolicy.getShouldAlignText()) return textAlignment;
    return null;
  }

  private Wrap getTagEndWrapping(final Formatter formatter, final XmlTag parent) {
    return formatter.createWrap(myXmlFormattingPolicy.getWrappingTypeForTagEnd(parent), true);
  }

  protected Wrap chooseWrap(final ASTNode child, final Wrap tagBeginWrap, final Wrap attrWrap, final Wrap textWrap) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return textWrap;
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE) return attrWrap;
    if (elementType == getTagType()) return tagBeginWrap;
    if (elementType == ElementType.XML_END_TAG_START) {
      final PsiElement parent = SourceTreeToPsiMap.treeElementToPsi(child.getTreeParent());
      if ((parent instanceof XmlTag) && ((XmlTag)parent).getSubTags().length > 0) {
        return getTagEndWrapping(getFormatter(), (XmlTag)parent);
      } else {
        return null;
      }
    }
    if (elementType == ElementType.XML_TEXT || elementType == ElementType.XML_DATA_CHARACTERS) return textWrap;
    return null;
  }

  public Wrap getWrap() {
    return myWrap;
  }

  public Indent getIndent() {
    return null;
  }

  public Alignment getAlignment() {
    return myAlignment;
  }

  protected Formatter getFormatter() {
    return Formatter.getInstance();
  }

  protected IElementType getTagType() {
    return myXmlFormattingPolicy.getTagType();
  }

  protected int getMaxLine(int defaultLineForDoNotKeepLineBreaksMode) {
    if (!myXmlFormattingPolicy.getShouldKeepLineBreaks()) return defaultLineForDoNotKeepLineBreaksMode;
    return myXmlFormattingPolicy.getKeepBlankLines() + 1;
  }

  public Block getParent() {
    return myParent;
  }

  protected XmlTag getTag() {
    return getTag(myNode);
  }

  protected XmlTag getTag(final ASTNode node) {
    return (XmlTag)SourceTreeToPsiMap.treeElementToPsi(node);
  }

  public void setParent(final Block synteticBlock) {
    myParent = synteticBlock;
  }
  protected Wrap createTagBeginWrapping(final Formatter formatter) {
    return formatter.createWrap(myXmlFormattingPolicy.getWrappingTypeForTagBegin(), true);
  }

  protected Block createChildBlock(final ASTNode child, final Wrap wrap, final Alignment alignment) {
    if (child.getElementType() == getTagType()) {
      return new XmlTagBlock(child, wrap, alignment, this, myXmlFormattingPolicy);
    } else {
      return new XmlBlock(child, wrap, alignment, this, myXmlFormattingPolicy);
    }
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  public abstract boolean insertLineBreakBeforeTag();

  public abstract boolean removeLineBreakBeforeTag();

  protected SpaceProperty createDefaultSpace(final int defaultLineForDoNotKeepLineBreaksMode) {
    return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, getMaxLine(defaultLineForDoNotKeepLineBreaksMode));
  }

  public abstract boolean isTextElement();

  public static Block creareRoot(final PsiFile element, final CodeStyleSettings settings) {
    final ASTNode rootNode = SourceTreeToPsiMap.psiElementToTree(element);
    if (rootNode.getElementType() == ElementType.XML_FILE) {
      return new XmlBlock(rootNode, null, null, null, new XmlPolicy(settings));
    } else {
      return new XmlBlock(rootNode, null, null, null, new HtmlPolicy(settings, ElementType.HTML_TAG));
    }
  }
}
