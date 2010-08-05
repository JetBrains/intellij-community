/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

public class XmlPolicy extends XmlFormattingPolicy{
  private final CodeStyleSettings mySettings;

  public XmlPolicy(final CodeStyleSettings settings, final FormattingDocumentModel documentModel) {
    super(documentModel);
    mySettings = settings;
  }

  public boolean indentChildrenOf(final XmlTag parentTag) {
    return !(parentTag.getFirstChild() instanceof PsiErrorElement);
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
    final PsiElement element = tag.getNextSibling();
    if (element instanceof XmlText && !(element.getFirstChild() instanceof PsiWhiteSpace) && tag.getSubTags().length == 0) return WrapType.NORMAL;
    return WrapType.ALWAYS;
  }

  public boolean isTextElement(XmlTag tag) {
    return false;
  }

  public boolean keepWhiteSpacesInsideTag(final XmlTag tag) {
    return false;
  }

  public int getTextWrap(final XmlTag tag) {
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
    return mySettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE;
  }

  public boolean getShouldAddSpaceAroundTagName() {
    return mySettings.XML_SPACE_AFTER_TAG_NAME;
  }

  public int getKeepBlankLines() {
    return mySettings.XML_KEEP_BLANK_LINES;
  }

  public boolean getShouldKeepLineBreaks() {
    return mySettings.XML_KEEP_LINE_BREAKS;
  }

  public boolean getShouldKeepLineBreaksInText() {
    return mySettings.XML_KEEP_LINE_BREAKS_IN_TEXT;
  }

  @Override
  public boolean getKeepWhiteSpacesInsideCDATA() {
    return mySettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA;
  }

  @Override
  public int getWhiteSpaceAroundCDATAOption() {
    return mySettings.XML_WHITE_SPACE_AROUND_CDATA;
  }

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  public boolean addSpaceIntoEmptyTag() {
    return mySettings.XML_SPACE_INSIDE_EMPTY_TAG;
  }

  public boolean shouldSaveSpacesBetweenTagAndText() {
    return false;
  }

}
