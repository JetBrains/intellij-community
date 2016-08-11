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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;

import java.util.HashMap;
import java.util.Map;

public class HtmlPolicy extends XmlFormattingPolicy {

  protected final CodeStyleSettings mySettings;

  public HtmlPolicy(final CodeStyleSettings settings, final FormattingDocumentModel documentModel) {
    super(documentModel);
    mySettings = settings;
  }

  @Override
  public boolean indentChildrenOf(final XmlTag parentTag) {
    if (parentTag == null) {
      return true;
    }
    final PsiElement firstChild = findFirstNonEmptyChild(parentTag);

    if (firstChild == null) {
      return false;
    }

    if (firstChild.getNode().getElementType() != XmlTokenType.XML_START_TAG_START) {
      return false;
    }

    if (mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES > 0 &&
        getLines(parentTag) > mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES) {
      return false;
    }
    else {
      return !checkName(parentTag, mySettings.HTML_DO_NOT_INDENT_CHILDREN_OF);
    }
  }

  private PsiElement findFirstNonEmptyChild(final XmlTag parentTag) {
    PsiElement result = parentTag.getFirstChild();
    while (result != null && result.getTextLength() == 0) {
      result = result.getNextSibling();
    }
    return result;
  }

  private int getLines(final XmlTag parentTag) {
    final TextRange textRange = parentTag.getTextRange();
    return myDocumentModel.getLineNumber(textRange.getEndOffset()) - myDocumentModel.getLineNumber(textRange.getStartOffset()) + 1;
  }

  @Override
  public boolean insertLineBreakBeforeTag(final XmlTag xmlTag) {
    PsiElement prev = xmlTag.getPrevSibling();
    if (prev == null) return false;
    ASTNode prevNode = SourceTreeToPsiMap.psiElementToTree(prev);
    while (prevNode != null && containsWhiteSpacesOnly(prevNode)) {
      prevNode = prevNode.getTreePrev();
    }
    if (prevNode == null) return false;
    if (!(SourceTreeToPsiMap.treeElementToPsi(prevNode) instanceof XmlTag)) return false;
    return checkName(xmlTag, mySettings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE);
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

  private boolean containsWhiteSpacesOnly(final ASTNode node) {
    if (node == null) return false;
    if (node.getElementType() == TokenType.WHITE_SPACE) return true;
    if (node instanceof LeafElement) return false;
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child)) return false;
      child = child.getTreeNext();
    }
    return true;
  }

  @Override
  public boolean removeLineBreakBeforeTag(final XmlTag xmlTag) {
    return checkName(xmlTag, mySettings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
  }

  protected boolean checkName(XmlTag tag, String option) {
    if (option == null) return false;
    for (String name : getTagNames(option)) {
      if (name.trim().equalsIgnoreCase(tag.getName())) return true;
    }
    return false;
  }

  private final Map<String, String[]> myCachedSplits = new HashMap<>();

  private String[] getTagNames(final String option) {
    String[] splits = myCachedSplits.get(option);
    if (splits == null) {
      splits = option.split(",");
      myCachedSplits.put(option, splits);
    }
    return splits;
  }

  @Override
  public boolean keepWhiteSpacesInsideTag(final XmlTag tag) {
    XmlTag current = tag;
    while (current != null) {
      if (checkName(current, mySettings.HTML_KEEP_WHITESPACES_INSIDE) || "jsp:attribute".equals(current.getName())) {
        return true;
      }
      current = current.getParentTag();
    }
    return false;
  }

  @Override
  public WrapType getWrappingTypeForTagEnd(final XmlTag xmlTag) {
    return shouldBeWrapped(xmlTag) ? WrapType.ALWAYS : WrapType.NORMAL;
  }

  @Override
  public WrapType getWrappingTypeForTagBegin(final XmlTag tag) {
    if (shouldBeWrapped(tag)) {
      return WrapType.ALWAYS;
    }

    if (!isInlineTag(tag)) {

      if (checkName(tag, mySettings.HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT)) {
        if (hasInlineContentOnly(tag)) return WrapType.NORMAL;
      }

      return WrapType.ALWAYS;
    }

    return WrapType.NORMAL;
  }

  private boolean hasInlineContentOnly(final XmlTag tag) {
    final XmlTag[] tags = tag.getSubTags();
    for (XmlTag xmlTag : tags) {
      if (!isInlineTag(xmlTag)) return false;
      if (!hasInlineContentOnly(xmlTag)) return false;
    }

    return true;
  }

  protected boolean isInlineTag(final XmlTag tag) {
    return checkName(tag, mySettings.HTML_INLINE_ELEMENTS);
  }

  protected boolean shouldBeWrapped(final XmlTag tag) {
    return false;
  }

  @Override
  public boolean isTextElement(XmlTag tag) {
    return isInlineTag(tag);
  }

  @Override
  public int getTextWrap(final XmlTag tag) {
    return mySettings.HTML_TEXT_WRAP;
  }

  @Override
  public int getAttributesWrap() {
    return mySettings.HTML_ATTRIBUTE_WRAP;
  }

  @Override
  public boolean getShouldAlignAttributes() {
    return mySettings.HTML_ALIGN_ATTRIBUTES;
  }

  @Override
  public boolean getShouldAlignText() {
    return mySettings.HTML_ALIGN_TEXT;
  }

  @Override
  public boolean getShouldKeepWhiteSpaces() {
    return mySettings.HTML_KEEP_WHITESPACES;
  }

  @Override
  public boolean getShouldAddSpaceAroundEqualityInAttribute() {
    return mySettings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE;
  }

  @Override
  public boolean getShouldAddSpaceAroundTagName() {
    return mySettings.HTML_SPACE_AFTER_TAG_NAME;
  }

  @Override
  public int getKeepBlankLines() {
    return mySettings.HTML_KEEP_BLANK_LINES;
  }

  @Override
  public boolean getShouldKeepLineBreaks() {
    return mySettings.HTML_KEEP_LINE_BREAKS;
  }

  @Override
  public boolean getShouldKeepLineBreaksInText() {
    return mySettings.HTML_KEEP_LINE_BREAKS_IN_TEXT;
  }

  @Override
  public boolean getKeepWhiteSpacesInsideCDATA() {
    return true;
  }

  @Override
  public int getWhiteSpaceAroundCDATAOption() {
    return XmlCodeStyleSettings.WS_AROUND_CDATA_PRESERVE;
  }

  @Override
  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  @Override
  public boolean addSpaceIntoEmptyTag() {
    return mySettings.HTML_SPACE_INSIDE_EMPTY_TAG;
  }

  @Override
  public boolean shouldSaveSpacesBetweenTagAndText() {
    return true;
  }
}
