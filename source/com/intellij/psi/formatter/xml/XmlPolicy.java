package com.intellij.psi.formatter.xml;

import com.intellij.formatting.WrapType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;

public class XmlPolicy extends XmlFormattingPolicy{
  private final CodeStyleSettings mySettings;

  public XmlPolicy(final CodeStyleSettings settings) {
    mySettings = settings;
  }

  public boolean indentChildrenOf(final XmlTag parentTag) {

    if( XmlChildRole.START_TAG_START_FINDER.findChild(parentTag.getNode()) == null ) {
      return false;
    }

    return true;
  }

  public boolean insertLineBreakBeforeTag(final XmlTag xmlTag) {
    return false;
  }

  public boolean removeLineBreakBeforeTag(final XmlTag xmlTag) {
    return false;
  }

  public WrapType getWrappingTypeForTagEnd(final XmlTag xmlTag) {
    return xmlTag.getSubTags().length > 0 ? WrapType.ALWAYS
           : WrapType.NORMAL;
  }

  public WrapType getWrappingTypeForTagBegin(final XmlTag tag) {
    return WrapType.ALWAYS;
  }

  public boolean isTextElement(XmlTag tag) {
    return false;
  }

  public boolean keepWhiteSpacesInsideTag(final XmlTag tag) {
    return false;
  }

  public int getTextWrap() {
    return mySettings.XML_TEXT_WRAP;
  }

  public int getAttributesWrap() {
    return mySettings.XML_ATTRIBUTE_WRAP;
  }

  public boolean getShouldAlignAttributes() {
    return mySettings.XML_ALIGN_ATTRIBUTES;
  }
  public boolean getShouldAlignText() {
    return mySettings.XML_ALIGN_TEXT;
  }

  public boolean getShouldKeepWhiteSpaces() {
    return mySettings.XML_KEEP_WHITESPACES;
  }

  public boolean getShouldAddSpaceAroundEqualityInAttribute() {
    return mySettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE;
  }

  public boolean getShouldAddSpaceAroundTagName() {
    return mySettings.XML_SPACE_AROUND_TAG_NAME;
  }

  public int getKeepBlankLines() {
    return mySettings.XML_KEEP_BLANK_LINES;
  }

  public boolean getShouldKeepLineBreaks() {
    return mySettings.XML_KEEP_LINE_BREAKS;
  }

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  public boolean addSpaceIntoEmptyTag() {
    return mySettings.XML_SPACE_INSIDE_EMPTY_TAG;
  }

}
