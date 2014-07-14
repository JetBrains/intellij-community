/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.formatter.xml;

import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.WrapType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

public class XmlPolicy extends XmlFormattingPolicy{
  private final CodeStyleSettings mySettings;
  private final XmlCodeStyleSettings myXmlSettings;

  public XmlPolicy(final CodeStyleSettings settings, final FormattingDocumentModel documentModel) {
    super(documentModel);
    mySettings = settings;
    myXmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
  }

  @Override
  public boolean indentChildrenOf(final XmlTag parentTag) {
    return !(parentTag.getFirstChild() instanceof PsiErrorElement);
  }

  @Override
  public boolean insertLineBreakBeforeTag(final XmlTag xmlTag) {
    return false;
  }

  @Override
  public boolean insertLineBreakBeforeFirstAttribute(XmlAttribute attribute) {
    return false;
  }

  @Override
  public boolean insertLineBreakAfterLastAttribute(XmlAttribute attribute) {
    return false;
  }

  @Override
  public boolean insertLineBreakAfterTagBegin(XmlTag tag) {
    return false;
  }

  @Override
  public boolean removeLineBreakBeforeTag(final XmlTag xmlTag) {
    return false;
  }

  @Override
  public WrapType getWrappingTypeForTagEnd(final XmlTag xmlTag) {
    return xmlTag.getSubTags().length > 0 ? WrapType.ALWAYS
           : WrapType.NORMAL;
  }

  @Override
  public WrapType getWrappingTypeForTagBegin(final XmlTag tag) {
    final PsiElement element = tag.getNextSibling();
    if (element instanceof XmlText && !(element.getFirstChild() instanceof PsiWhiteSpace) && tag.getSubTags().length == 0) return WrapType.NORMAL;
    return WrapType.ALWAYS;
  }

  @Override
  public boolean isTextElement(XmlTag tag) {
    return false;
  }

  @Override
  public boolean keepWhiteSpacesInsideTag(final XmlTag tag) {
    return false;
  }

  @Override
  public int getTextWrap(final XmlTag tag) {
    return myXmlSettings.XML_TEXT_WRAP;
  }

  @Override
  public int getAttributesWrap() {
    return myXmlSettings.XML_ATTRIBUTE_WRAP;
  }

  @Override
  public boolean getShouldAlignAttributes() {
    return myXmlSettings.XML_ALIGN_ATTRIBUTES;
  }
  @Override
  public boolean getShouldAlignText() {
    return myXmlSettings.XML_ALIGN_TEXT;
  }

  @Override
  public boolean getShouldKeepWhiteSpaces() {
    return myXmlSettings.XML_KEEP_WHITESPACES;
  }

  @Override
  public boolean getShouldAddSpaceAroundEqualityInAttribute() {
    return myXmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE;
  }

  @Override
  public boolean getShouldAddSpaceAroundTagName() {
    return myXmlSettings.XML_SPACE_AFTER_TAG_NAME;
  }

  @Override
  public int getKeepBlankLines() {
    return myXmlSettings.XML_KEEP_BLANK_LINES;
  }

  @Override
  public boolean getShouldKeepLineBreaks() {
    return myXmlSettings.XML_KEEP_LINE_BREAKS;
  }

  @Override
  public boolean getShouldKeepLineBreaksInText() {
    return myXmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT;
  }

  @Override
  public boolean getKeepWhiteSpacesInsideCDATA() {
    return myXmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA;
  }

  @Override
  public int getWhiteSpaceAroundCDATAOption() {
    return myXmlSettings.XML_WHITE_SPACE_AROUND_CDATA;
  }

  @Override
  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  @Override
  public boolean addSpaceIntoEmptyTag() {
    return myXmlSettings.XML_SPACE_INSIDE_EMPTY_TAG;
  }

  @Override
  public boolean shouldSaveSpacesBetweenTagAndText() {
    return false;
  }

}
