package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.Alignment;
import com.intellij.newCodeFormatting.Wrap;
import com.intellij.newCodeFormatting.Block;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.xml.XmlTag;

public class XmlBlock extends AbstractXmlBlock {


  protected XmlBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, XmlBlock parent, CodeStyleSettings settings) {
    super(node, wrap, alignment, parent, settings);
  }

  protected IElementType getTagType() {
    return ElementType.XML_TAG;
  }

  protected boolean indentChildrenOf(final XmlTag parentTag) {
    return true;
  }

  protected boolean insertLineBreakBeforeTag(final XmlTag xmlTag) {
    return false;
  }

  protected boolean removeLineBreakBeforeTag(final XmlTag xmlTag) {
    return false;
  }

  protected Wrap.Type getWrappingTypeForTagEnd(final XmlTag xmlTag) {
    return xmlTag.getSubTags().length > 0 ? Wrap.Type.WRAP_ALWAYS : Wrap.Type.WRAP_AS_NEEDED;
  }

  protected Wrap.Type getWrappingTypeForTagBegin() {
    return Wrap.Type.WRAP_ALWAYS;
  }

  protected boolean isTextElement(XmlTag tag) {
    return false;
  }

  protected boolean keepWhiteSpacesInsideTag(final XmlTag tag) {
    return false;
  }

  protected int getTextWrap() {
    return mySettings.XML_TEXT_WRAP;
  }

  protected int getAttributesWrap() {
    return mySettings.XML_ATTRIBUTE_WRAP;
  }

  protected boolean getShouldAlignAttributes() {
    return mySettings.XML_ALIGN_ATTRIBUTES;
  }

  protected boolean getShouldAlignText() {
    return mySettings.XML_ALIGN_TEXT;
  }

  protected boolean getShouldKeepWhiteSpaces() {
    return mySettings.XML_KEEP_WHITESPACES;
  }

  protected boolean getShouldAddSpaceAroundEqualityInAttribute() {
    return mySettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE;
  }

  protected boolean getShouldAddSpaceAroundTagName() {
    return mySettings.XML_SPACE_AROUND_TAG_NAME;
  }

  protected int getKeepBlankLines() {
    return mySettings.XML_KEEP_BLANK_LINES;
  }

  protected boolean getShouldKeepLineBreaks() {
    return mySettings.XML_KEEP_LINE_BREAKS;
  }

  protected Block createChildBlock(final ASTNode child,
                                   final Wrap wrap,
                                   final Alignment alignment) {
    return new XmlBlock(child, wrap, alignment, this, mySettings);
  }
}
