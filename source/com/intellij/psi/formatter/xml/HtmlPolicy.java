package com.intellij.psi.formatter.xml;

import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.jsp.JspUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;

public class HtmlPolicy extends XmlFormattingPolicy{
  private CodeStyleSettings mySettings;

  public HtmlPolicy(final CodeStyleSettings settings, final FormattingDocumentModel documentModel) {
    super(documentModel);
    mySettings = settings;

  }

  public boolean indentChildrenOf(final XmlTag parentTag) {
    if (parentTag == null) {
      return true;
    }
    final PsiElement firstChild = findFirstNonEmptyChild(parentTag);

    if( firstChild == null )
    {
      return false;
    }

    if( firstChild.getNode().getElementType() != ElementType.XML_START_TAG_START )
    {
      return false;
    }

    if (mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES > 0 && getLines(parentTag) > mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES) {
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
    return myDocumentModel.getLineNumber(textRange.getEndOffset()) - myDocumentModel.getLineNumber(textRange.getStartOffset());
  }

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

  private boolean containsWhiteSpacesOnly(final ASTNode node) {
    if (node == null) return false;
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

  public boolean removeLineBreakBeforeTag(final XmlTag xmlTag) {
    return checkName(xmlTag, mySettings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
  }

  private boolean checkName(XmlTag tag, String option) {
    if (option == null) return false;
    final String[] names = option.split(",");
    for (String name : names) {
      if (name.trim().equalsIgnoreCase(tag.getName())) return true;
    }
    return false;
  }

  public boolean keepWhiteSpacesInsideTag(final XmlTag tag) {
    return checkName(tag, mySettings.HTML_KEEP_WHITESPACES_INSIDE);
  }

  public WrapType getWrappingTypeForTagEnd(final XmlTag xmlTag) {
    return shouldBeWrapped(xmlTag)? WrapType.ALWAYS : WrapType.NORMAL;
  }

  public WrapType getWrappingTypeForTagBegin(final XmlTag tag) {
    if (shouldBeWrapped(tag) || checkName(tag, mySettings.HTML_PLACE_ON_NEW_LINE)) {
      return WrapType.ALWAYS;
    } else {
      return WrapType.NORMAL;
    }
  }

  private boolean shouldBeWrapped(final XmlTag tag) {
    final String name = tag.getName();
    if (name == null) return false;

    if (isScriptletObject(tag)) return true;
    return JspUtil.getDirectiveKindByTag(tag) != null;
  }

  private boolean isScriptletObject(final XmlTag tag) {
    return XmlUtil.JSP_URI.equals(tag.getNamespace());
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

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  public boolean addSpaceIntoEmptyTag() {
    return mySettings.HTML_SPACE_INSIDE_EMPTY_TAG;
  }

  public boolean shouldSaveSpacesBetweenTagAndText() {
    return true;
  }

}
