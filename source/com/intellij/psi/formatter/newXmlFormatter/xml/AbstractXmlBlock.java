package com.intellij.psi.formatter.newXmlFormatter.xml;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;


public abstract class AbstractXmlBlock extends AbstractBlock {
  protected final XmlFormattingPolicy myXmlFormattingPolicy;

  public AbstractXmlBlock(final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          final XmlFormattingPolicy policy) {
    super(node, wrap, alignment);
    myXmlFormattingPolicy = policy;
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
    if (elementType == ElementType.XML_START_TAG_START) return tagBeginWrap;
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

  protected XmlTag getTag() {
    return getTag(myNode);
  }

  protected XmlTag getTag(final ASTNode node) {
    return (XmlTag)SourceTreeToPsiMap.treeElementToPsi(node);
  }

  protected Wrap createTagBeginWrapping(final Formatter formatter) {
    return formatter.createWrap(myXmlFormattingPolicy.getWrappingTypeForTagBegin(), true);
  }

  protected Block createChildBlock(final ASTNode child, final Wrap wrap, final Alignment alignment) {
    if (child.getElementType() == getTagType()) {
      return new XmlTagBlock(child, wrap, alignment, myXmlFormattingPolicy);
    } else {
      return new XmlBlock(child, wrap, alignment, myXmlFormattingPolicy);
    }
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  public abstract boolean insertLineBreakBeforeTag();

  public abstract boolean removeLineBreakBeforeTag();

  protected SpaceProperty createDefaultSpace(boolean forceKeepLineBreaks) {
    boolean shouldKeepLineBreaks = myXmlFormattingPolicy.getShouldKeepLineBreaks() || forceKeepLineBreaks;    
    return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, shouldKeepLineBreaks, myXmlFormattingPolicy.getKeepBlankLines());
  }

  public abstract boolean isTextElement();

  public static Block creareRoot(final PsiFile element, final CodeStyleSettings settings) {
    final ASTNode rootNode = SourceTreeToPsiMap.psiElementToTree(element);
    if (rootNode.getElementType() == ElementType.XML_FILE) {
      return new XmlBlock(rootNode, null, null, new XmlPolicy(settings));
    } else {
      return new XmlBlock(rootNode, null, null, new HtmlPolicy(settings, ElementType.HTML_TAG));
    }
  }
}
