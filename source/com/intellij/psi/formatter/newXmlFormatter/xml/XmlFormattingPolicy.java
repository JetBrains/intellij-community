package com.intellij.psi.formatter.newXmlFormatter.xml;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.codeStyle.CodeStyleSettings;


public interface XmlFormattingPolicy {
  int getWrappingTypeForTagEnd(XmlTag xmlTag);

  int getWrappingTypeForTagBegin();

  boolean insertLineBreakBeforeTag(XmlTag xmlTag);

  boolean removeLineBreakBeforeTag(XmlTag xmlTag);

  boolean keepWhiteSpacesInsideTag(XmlTag tag);

  boolean indentChildrenOf(XmlTag parentTag);

  IElementType getTagType();

  boolean isTextElement(XmlTag tag);

  int getTextWrap();

  int getAttributesWrap();

  boolean getShouldAlignAttributes();

  boolean getShouldAlignText();

  boolean getShouldKeepWhiteSpaces();

  boolean getShouldAddSpaceAroundEqualityInAttribute();

  boolean getShouldAddSpaceAroundTagName();

  int getKeepBlankLines();

  boolean getShouldKeepLineBreaks();

  CodeStyleSettings getSettings();
}
