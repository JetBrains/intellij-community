package com.intellij.psi.formatter.newXmlFormatter;

import com.intellij.newCodeFormatting.Wrap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;

public class HtmlPolicy implements XmlFormattingPolicy{
  private final IElementType myHtmlTagType;
  private CodeStyleSettings mySettings;

  public HtmlPolicy(final CodeStyleSettings settings,
                   final IElementType htmlType) {
    myHtmlTagType = htmlType;
    mySettings = settings;
  }

  public IElementType getTagType() {
    return myHtmlTagType;
  }

  public boolean indentChildrenOf(final XmlTag parentTag) {
    if (parentTag == null) {
      return true;
    }
    else if (getLines(parentTag) > mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES) {
      return false;
    }
    else {
      return !checkName(parentTag, mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF);
    }
  }

  private int getLines(final XmlTag parentTag) {
    return StringUtil.getLineBreakCount(parentTag.getText());
  }

  public boolean insertLineBreakBeforeTag(final XmlTag xmlTag) {
    return checkName(xmlTag, mySettings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE);
  }

  public boolean removeLineBreakBeforeTag(final XmlTag xmlTag) {
    return checkName(xmlTag, mySettings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
  }

  private boolean checkName(XmlTag tag, String option) {
    if (option == null) return false;
    final String[] names = option.split(",");
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      if (name.trim().equalsIgnoreCase(tag.getName())) return true;
    }
    return false;
  }

  public boolean keepWhiteSpacesInsideTag(final XmlTag tag) {
    return checkName(tag, mySettings.HTML_KEEP_WHITESPACES_INSIDE);
  }

  public int getWrappingTypeForTagEnd(final XmlTag xmlTag) {
    return Wrap.NORMAL;
  }

  public int getWrappingTypeForTagBegin() {
    return Wrap.NORMAL;
  }

  public boolean isTextElement(XmlTag tag) {
    return checkName(tag, mySettings.HTML_TEXT_ELEMENTS);
  }

  public int getTextWrap() {
    return mySettings.HTML_TEXT_WRAP;
  }

  public int getAttributesWrap() {
    return mySettings.HTML_ATTRIBUTE_WRAP;
  }

  public boolean getShouldAlignAttributes() {
    return mySettings.HTML_ALIGN_ATTRIBUTES;
  }

  public boolean getShouldAlignText() {
    return mySettings.HTML_ALIGN_TEXT;
  }

  public boolean getShouldKeepWhiteSpaces() {
    return mySettings.HTML_KEEP_WHITESPACES;
  }

  public boolean getShouldAddSpaceAroundEqualityInAttribute() {
    return mySettings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE;
  }

  public boolean getShouldAddSpaceAroundTagName() {
    return mySettings.HTML_SPACE_AROUND_TAG_NAME;
  }

  public int getKeepBlankLines() {
    return mySettings.HTML_KEEP_BLANK_LINES;
  }

  public boolean getShouldKeepLineBreaks() {
    return mySettings.HTML_KEEP_LINE_BREAKS;
  }

}
