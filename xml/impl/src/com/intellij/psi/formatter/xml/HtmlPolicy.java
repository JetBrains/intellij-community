// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.xml;

import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class HtmlPolicy extends XmlFormattingPolicy {
  private static final TokenSet TAG_END_TOKEN_SET = TokenSet.create(XmlTokenType.XML_TAG_END, XmlTokenType.XML_EMPTY_ELEMENT_END);

  protected final HtmlCodeStyleSettings myHtmlCodeStyleSettings;
  protected final CodeStyleSettings myRootSettings;

  public HtmlPolicy(final CodeStyleSettings settings, final FormattingDocumentModel documentModel) {
    super(documentModel);
    myRootSettings = settings;
    myHtmlCodeStyleSettings = settings.getCustomSettings(HtmlCodeStyleSettings.class);
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

    if (myHtmlCodeStyleSettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES > 0 &&
        getLines(parentTag) > myHtmlCodeStyleSettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES) {
      return false;
    }
    else {
      return !checkName(parentTag, myHtmlCodeStyleSettings.HTML_DO_NOT_INDENT_CHILDREN_OF);
    }
  }

  protected PsiElement findFirstNonEmptyChild(final XmlTag parentTag) {
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
    return checkName(xmlTag, myHtmlCodeStyleSettings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE);
  }

  @Override
  public boolean insertLineBreakAfterTagBegin(XmlTag tag) {
    return false;
  }

  private static boolean containsWhiteSpacesOnly(final ASTNode node) {
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
    return checkName(xmlTag, myHtmlCodeStyleSettings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
  }

  protected boolean checkName(XmlTag tag, String option) {
    return checkName(tag, option, true);
  }

  protected boolean checkName(XmlTag tag, String option, boolean ignoreCase) {
    if (option == null) return false;
    for (String name : getTagNames(option)) {
      String optionName = name.trim();
      String tagName = tag.getName();
      if (ignoreCase ? optionName.equalsIgnoreCase(tagName) : optionName.equals(tagName)) return true;
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
      if (checkName(current, myHtmlCodeStyleSettings.HTML_KEEP_WHITESPACES_INSIDE) || "jsp:attribute".equals(current.getName())) {
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

      if (checkName(tag, myHtmlCodeStyleSettings.HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT)) {
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
    return checkName(tag, myHtmlCodeStyleSettings.HTML_INLINE_ELEMENTS);
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
    return myHtmlCodeStyleSettings.HTML_TEXT_WRAP;
  }

  @Override
  public int getAttributesWrap() {
    return myHtmlCodeStyleSettings.HTML_ATTRIBUTE_WRAP;
  }

  @Override
  public boolean getShouldAlignAttributes() {
    return myHtmlCodeStyleSettings.HTML_ALIGN_ATTRIBUTES;
  }

  @Override
  public boolean getShouldAlignText() {
    return myHtmlCodeStyleSettings.HTML_ALIGN_TEXT;
  }

  @Override
  public boolean getShouldKeepWhiteSpaces() {
    return myHtmlCodeStyleSettings.HTML_KEEP_WHITESPACES;
  }

  @Override
  public boolean getShouldAddSpaceAroundEqualityInAttribute() {
    return myHtmlCodeStyleSettings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE;
  }

  @Override
  public boolean getShouldAddSpaceAroundTagName() {
    return myHtmlCodeStyleSettings.HTML_SPACE_AFTER_TAG_NAME;
  }

  @Override
  public int getKeepBlankLines() {
    return myHtmlCodeStyleSettings.HTML_KEEP_BLANK_LINES;
  }

  @Override
  public boolean getShouldKeepLineBreaks() {
    return myHtmlCodeStyleSettings.HTML_KEEP_LINE_BREAKS;
  }

  @Override
  public boolean getShouldKeepLineBreaksInText() {
    return myHtmlCodeStyleSettings.HTML_KEEP_LINE_BREAKS_IN_TEXT;
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
    return myRootSettings;
  }

  @Override
  public boolean addSpaceIntoEmptyTag() {
    return myHtmlCodeStyleSettings.HTML_SPACE_INSIDE_EMPTY_TAG;
  }

  @Override
  public boolean shouldSaveSpacesBetweenTagAndText() {
    return true;
  }

  @Override
  public @Nullable Spacing getSpacingBeforeFirstAttribute(XmlAttribute attribute) {
    boolean isEnabled = myHtmlCodeStyleSettings.HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE == CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    return getStartTagDependantSpacingOrNull(attribute.getParent(), isEnabled, 1);
  }

  @Override
  public @Nullable Spacing getSpacingAfterLastAttribute(XmlAttribute attribute) {
    XmlTag parent = attribute.getParent();
    final int spaces = addSpaceIntoEmptyTag() && parent.isEmpty() && FormatterUtil.isFollowedBy(attribute.getNode(), XmlTokenType.XML_EMPTY_ELEMENT_END) ? 1 : 0;
    boolean isEnabled = myHtmlCodeStyleSettings.HTML_NEWLINE_AFTER_LAST_ATTRIBUTE == CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    return getStartTagDependantSpacingOrNull(parent, isEnabled, spaces);
  }

  private @Nullable Spacing getStartTagDependantSpacingOrNull(XmlTag tag, boolean enabled, int spaces) {
    if (!enabled) return null;
    TextRange range = getStartTagRange(tag);
    if (range == null) return null;
    return Spacing.createDependentLFSpacing(spaces, spaces, range, getShouldKeepLineBreaks(), getKeepBlankLines());
  }

  private static @Nullable TextRange getStartTagRange(XmlTag tag) {
    ASTNode start = tag.getNode().findChildByType(XmlTokenType.XML_START_TAG_START);
    ASTNode end = tag.getNode().findChildByType(TAG_END_TOKEN_SET);
    return start != null && end != null
           ? new TextRange(start.getTextRange().getStartOffset(), end.getTextRange().getEndOffset()) : null;
  }
}
