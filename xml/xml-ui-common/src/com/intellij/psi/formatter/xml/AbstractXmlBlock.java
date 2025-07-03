// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.xml;

import com.intellij.formatting.*;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.*;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyFactory;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public abstract class AbstractXmlBlock extends AbstractBlock {
  protected XmlFormattingPolicy myXmlFormattingPolicy;
  protected final XmlInjectedLanguageBlockBuilder myInjectedBlockBuilder;
  private final boolean myPreserveSpace;

  protected AbstractXmlBlock(@NotNull ASTNode node,
                             @Nullable Wrap wrap,
                             @Nullable Alignment alignment,
                             @NotNull XmlFormattingPolicy policy) {
    this(node, wrap, alignment, policy, false);
  }


  protected AbstractXmlBlock(@NotNull ASTNode node,
                             @Nullable Wrap wrap,
                             @Nullable Alignment alignment,
                             @NotNull XmlFormattingPolicy policy,
                             final boolean preserveSpace) {
    super(node, wrap, alignment);
    myXmlFormattingPolicy = policy;
    if (node.getTreeParent() == null) {
      myXmlFormattingPolicy.setRootBlock(node, this);
    }
    myInjectedBlockBuilder = new XmlInjectedLanguageBlockBuilder(myXmlFormattingPolicy);
    myPreserveSpace = shouldPreserveSpace(node, preserveSpace);
  }


  /**
   * Handles xml:space='preserve|default' attribute.
   * See <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#sec-white-space">Extensible Markup Language (XML) 1.0 (Third Edition),
   * White Space Handling</a>
   *
   * @return True if the space must be preserved (xml:space='preserve'), false if the attribute
   * contains 'default'. If the attribute is not defined, return the current value.
   */
  private static boolean shouldPreserveSpace(ASTNode node, boolean defaultValue) {
    if (node.getPsi() instanceof XmlTag tag) {
      XmlAttribute spaceAttr = tag.getAttribute("xml:space");
      if (spaceAttr != null) {
        String value = spaceAttr.getValue();
        if ("preserve".equals(value)) {
          return true;
        }
        if ("default".equals(value)) {
          return false;
        }
      }
    }
    return defaultValue;
  }

  public boolean isPreserveSpace() {
    return myPreserveSpace;
  }


  public static @NotNull WrapType getWrapType(final int type) {
    if (type == CommonCodeStyleSettings.DO_NOT_WRAP) return WrapType.NONE;
    if (type == CommonCodeStyleSettings.WRAP_ALWAYS) return WrapType.ALWAYS;
    if (type == CommonCodeStyleSettings.WRAP_AS_NEEDED) return WrapType.NORMAL;
    return WrapType.CHOP_DOWN_IF_LONG;
  }

  protected boolean isTextNode(IElementType elementType) {
    return elementType == XmlElementType.XML_TEXT
           || elementType == XmlElementType.HTML_RAW_TEXT;
  }

  protected @Nullable Alignment chooseAlignment(@NotNull ASTNode child,
                                                @Nullable Alignment attrAlignment,
                                                @Nullable Alignment textAlignment) {
    if (isTextNode(myNode.getElementType())) return getAlignment();
    final IElementType elementType = child.getElementType();
    if (isAttributeElementType(elementType) && myXmlFormattingPolicy.getShouldAlignAttributes()) return attrAlignment;
    if (isTextNode(elementType) && myXmlFormattingPolicy.getShouldAlignText()) return textAlignment;
    return null;
  }

  private Wrap getTagEndWrapping(final XmlTag parent) {
    return Wrap.createWrap(myXmlFormattingPolicy.getWrappingTypeForTagEnd(parent), true);
  }

  protected Wrap chooseWrap(final ASTNode child, final Wrap tagBeginWrap, final Wrap attrWrap, final Wrap textWrap) {
    if (isTextNode(myNode.getElementType())) return textWrap;
    final IElementType elementType = child.getElementType();
    if (isAttributeElementType(elementType)) return attrWrap;
    if (elementType == XmlTokenType.XML_START_TAG_START) return tagBeginWrap;
    if (elementType == XmlTokenType.XML_END_TAG_START) {
      final PsiElement parent = SourceTreeToPsiMap.treeElementToPsi(child.getTreeParent());
      if (parent instanceof XmlTag tag) {
        if (canWrapTagEnd(tag)) {
          return getTagEndWrapping(tag);
        }
      }
      return null;
    }
    if (isTextNode(elementType) || elementType == XmlTokenType.XML_DATA_CHARACTERS) {
      ASTNode previous = FormatterUtil.getPreviousNonWhitespaceSibling(child);
      if (previous == null || !isTextNode(previous.getElementType())) {
        return myXmlFormattingPolicy.allowWrapBeforeText() ? textWrap : null;
      }
      return textWrap;
    }
    return null;
  }

  protected boolean canWrapTagEnd(final XmlTag tag) {
    return hasSubTags(tag);
  }

  static boolean hasSubTags(XmlTag tag) {
    PsiElement child = tag.getFirstChild();
    while (child != null) {
      if (child instanceof XmlTag) {
        return true;
      }
      child = child.getNextSibling();
    }
    return false;
  }

  protected XmlTag getTag() {
    return getTag(myNode);
  }

  protected static XmlTag getTag(final ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof XmlTag) {
      return (XmlTag)element;
    }
    else {
      return null;
    }
  }

  protected Wrap createTagBeginWrapping(final XmlTag tag) {
    return Wrap.createWrap(myXmlFormattingPolicy.getWrappingTypeForTagBegin(tag), true);
  }

  protected @Nullable ASTNode processChild(@NotNull List<Block> result,
                                           @NotNull ASTNode child,
                                           @Nullable Wrap wrap,
                                           @Nullable Alignment alignment,
                                           @Nullable Indent indent) {
    final Language myLanguage = myNode.getPsi().getLanguage();
    final PsiElement childPsi = child.getPsi();
    final Language childLanguage = childPsi.getLanguage();
    if (useMyFormatter(myLanguage, childLanguage, childPsi)) {

      XmlTag tag = getAnotherTreeTag(child);
      if (tag != null
          && containsTag(tag)
          && doesNotIntersectSubTagsWith(tag)) {
        ASTNode currentChild = createAnotherTreeNode(result, child, tag, indent, wrap, alignment);

        if (currentChild == null) {
          return null;
        }

        while (currentChild != null && currentChild.getTreeParent() != myNode && currentChild.getTreeParent() != child.getTreeParent()) {
          currentChild = processAllChildrenFrom(result, currentChild, wrap, alignment, indent);
          if (currentChild != null && (currentChild.getTreeParent() == myNode || currentChild.getTreeParent() == child.getTreeParent())) {
            return currentChild;
          }
          if (currentChild != null) {
            currentChild = currentChild.getTreeParent();
          }
        }

        return currentChild;
      }

      processSimpleChild(child, indent, result, wrap, alignment);
      return child;
    }
    else if (!isBuildIndentsOnly()) {
      myInjectedBlockBuilder.addInjectedLanguageBlockWrapper(result, child, indent, 0, null);
    }

    return child;
  }

  protected boolean doesNotIntersectSubTagsWith(final PsiElement tag) {
    final TextRange tagRange = tag.getTextRange();
    for (XmlTag subTag : JBIterable.of(myNode.getPsi().getChildren()).filter(XmlTag.class)) {
      final TextRange subTagRange = subTag.getTextRange();
      if (subTagRange.getEndOffset() < tagRange.getStartOffset()) continue;
      if (subTagRange.getStartOffset() > tagRange.getEndOffset()) return true;

      if (tagRange.getStartOffset() > subTagRange.getStartOffset() && tagRange.getEndOffset() < subTagRange.getEndOffset()) return false;
      if (tagRange.getEndOffset() > subTagRange.getStartOffset() && tagRange.getEndOffset() < subTagRange.getEndOffset()) return false;
    }
    return true;
  }

  protected boolean containsTag(final PsiElement tag) {
    final ASTNode closingTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(myNode);
    final ASTNode startTagStart = XmlChildRole.START_TAG_END_FINDER.findChild(myNode);

    if (closingTagStart == null && startTagStart == null) {
      return tag.getTextRange().getEndOffset() <= myNode.getTextRange().getEndOffset();
    }
    else if (closingTagStart == null) {
      return false;
    }
    else {
      return tag.getTextRange().getEndOffset() <= closingTagStart.getTextRange().getEndOffset();
    }
  }

  private ASTNode processAllChildrenFrom(final List<Block> result,
                                         final @NotNull ASTNode child,
                                         final Wrap wrap,
                                         final Alignment alignment,
                                         final Indent indent) {
    ASTNode resultNode = child;
    ASTNode currentChild = child.getTreeNext();
    while (currentChild != null && currentChild.getElementType() != XmlTokenType.XML_END_TAG_START) {
      if (!containsWhiteSpacesOnly(currentChild)) {
        currentChild = processChild(result, currentChild, wrap, alignment, indent);
        resultNode = currentChild;
      }
      if (currentChild != null) {
        currentChild = currentChild.getTreeNext();
      }
    }
    return resultNode;
  }

  protected void processSimpleChild(@NotNull ASTNode child,
                                    @Nullable Indent indent,
                                    @NotNull List<? super Block> result,
                                    @Nullable Wrap wrap,
                                    @Nullable Alignment alignment) {
    if (isXmlTag(child)) {
      result.add(createTagBlock(child, indent != null ? indent : Indent.getNoneIndent(), wrap, alignment));
    }
    else if (child.getElementType() == XmlElementType.XML_DOCTYPE) {
      result.add(
        new XmlBlock(child, wrap, alignment, myXmlFormattingPolicy, indent, null, isPreserveSpace()) {
          @Override
          protected Wrap getDefaultWrap(final ASTNode node) {
            final IElementType type = node.getElementType();
            return type == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
                   ? Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getAttributesWrap()), false) : null;
          }
        }
      );
    }
    else if (child.getElementType() == XmlTokenType.XML_DATA_CHARACTERS
             && child.getTreeParent().getElementType() != XmlElementType.XML_CDATA
             && child.textContains('\n')) {
      result.add(createRawTextChild(child, indent, wrap, alignment));
    }
    else {
      result.add(createSimpleChild(child, indent, wrap, alignment, null));
    }
  }

  protected @NotNull XmlBlock createRawTextChild(@NotNull ASTNode child, @Nullable Indent indent,
                                                @Nullable Wrap wrap, @Nullable Alignment alignment) {
    var text = child.getText();
    var textStart = StringUtil.skipWhitespaceOrNewLineForward(text, 0);
    var textEnd = StringUtil.skipWhitespaceOrNewLineBackward(text, text.length());
    TextRange textRange;
    if (textStart < textEnd) {
      textRange = child.getTextRange();
      textRange = new TextRange(textRange.getStartOffset() + textStart, textRange.getStartOffset() + textEnd);
    } else {
      textRange = null;
    }
    return new XmlBlock(child, wrap, alignment, myXmlFormattingPolicy, indent, textRange, isPreserveSpace());
  }

  protected @NotNull XmlBlock createSimpleChild(@NotNull ASTNode child, @Nullable Indent indent,
                                                @Nullable Wrap wrap, @Nullable Alignment alignment, @Nullable TextRange range) {
    return new XmlBlock(child, wrap, alignment, myXmlFormattingPolicy, indent, range, isPreserveSpace());
  }

  protected XmlTagBlock createTagBlock(@NotNull ASTNode child, @Nullable Indent indent, final Wrap wrap, final Alignment alignment) {
    return new XmlTagBlock(child, wrap, alignment, myXmlFormattingPolicy, indent != null ? indent : Indent.getNoneIndent(),
                           isPreserveSpace());
  }

  protected @Nullable XmlTag findXmlTagAt(final ASTNode child, final int startOffset) {
    return null;
  }

  protected @Nullable ASTNode createAnotherTreeNode(final List<? super Block> result,
                                                    final ASTNode child,
                                                    PsiElement tag,
                                                    final Indent indent,
                                                    final Wrap wrap, final Alignment alignment) {
    return null;
  }

  protected @Nullable Block createAnotherTreeTagBlock(final PsiElement tag, final Indent childIndent) {
    return null;
  }

  protected XmlFormattingPolicy createPolicyFor() {
    return myXmlFormattingPolicy;
  }

  protected @Nullable XmlTag getAnotherTreeTag(final ASTNode child) {
    return null;
  }

  protected boolean isAttributeElementType(final IElementType elementType) {
    return elementType instanceof IXmlAttributeElementType;
  }

  protected boolean isXmlTag(final ASTNode child) {
    return isXmlTag(child.getPsi());
  }

  protected boolean isXmlTag(final PsiElement psi) {
    return psi instanceof XmlTag;
  }

  protected boolean useMyFormatter(@NotNull Language myLanguage, @NotNull Language childLanguage, @NotNull PsiElement childPsi) {
    if (myLanguage == childLanguage ||
        childLanguage == HtmlFileType.INSTANCE.getLanguage() ||
        childLanguage == XHtmlFileType.INSTANCE.getLanguage() ||
        childLanguage == XmlFileType.INSTANCE.getLanguage()) {
      return true;
    }
    final FormattingModelBuilder childFormatter = LanguageFormatting.INSTANCE.forLanguage(childLanguage);
    return childFormatter == null ||
           childFormatter instanceof DelegatingFormattingModelBuilder &&
           ((DelegatingFormattingModelBuilder)childFormatter).dontFormatMyModel(childPsi);
  }

  protected boolean isJspxJavaContainingNode(final ASTNode child) {
    return false;
  }

  public abstract boolean insertLineBreakBeforeTag();

  public int getBlankLinesBeforeTag() {
    return insertLineBreakBeforeTag() ? 1 : 0;
  }

  public abstract boolean removeLineBreakBeforeTag();

  protected Spacing createDefaultSpace(boolean forceKeepLineBreaks, final boolean inText) {
    boolean shouldKeepLineBreaks = getShouldKeepLineBreaks(inText, forceKeepLineBreaks);
    return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, shouldKeepLineBreaks, myXmlFormattingPolicy.getKeepBlankLines());
  }

  private boolean getShouldKeepLineBreaks(final boolean inText, final boolean forceKeepLineBreaks) {
    if (forceKeepLineBreaks) {
      return true;
    }
    if (inText && myXmlFormattingPolicy.getShouldKeepLineBreaksInText()) {
      return true;
    }
    if (!inText && myXmlFormattingPolicy.getShouldKeepLineBreaks()) {
      return true;
    }
    return false;
  }

  public abstract boolean isTextElement();

  protected void createJspTextNode(final List<? super Block> localResult, final ASTNode child, final Indent indent) {
  }

  protected static @Nullable ASTNode findChildAfter(final @NotNull ASTNode child, final int endOffset) {
    TreeElement fileNode = TreeUtil.getFileElement((TreeElement)child);
    final LeafElement leaf = fileNode.findLeafElementAt(endOffset);
    if (leaf != null && leaf.getStartOffset() == endOffset && endOffset > 0) {
      return fileNode.findLeafElementAt(endOffset - 1);
    }
    return leaf;
  }

  @Override
  public boolean isLeaf() {
    return (isComment(myNode)) ||
           myNode.getElementType() == TokenType.WHITE_SPACE ||
           myNode.getElementType() == XmlTokenType.XML_DATA_CHARACTERS ||
           myNode.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
  }

  private static boolean isComment(final ASTNode node) {
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(node);
    if (psiElement instanceof PsiComment) return true;
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(psiElement.getLanguage());
    if (parserDefinition == null) return false;
    final TokenSet commentTokens = parserDefinition.getCommentTokens();
    return commentTokens.contains(node.getElementType());
  }

  public void setXmlFormattingPolicy(final XmlFormattingPolicy xmlFormattingPolicy) {
    myXmlFormattingPolicy = xmlFormattingPolicy;
  }

  protected boolean buildInjectedPsiBlocks(List<Block> result, final ASTNode child, Wrap wrap, Alignment alignment, Indent indent) {
    if (isBuildIndentsOnly()) return false;

    if (myInjectedBlockBuilder.addInjectedBlocks(result, child, wrap, alignment, indent)) {
      return true;
    }

    PsiFile containingFile = child.getPsi().getContainingFile();
    FileViewProvider fileViewProvider = containingFile.getViewProvider();

    if (fileViewProvider instanceof TemplateLanguageFileViewProvider) {
      Language templateLanguage = ((TemplateLanguageFileViewProvider)fileViewProvider).getTemplateDataLanguage();
      PsiElement at = fileViewProvider.findElementAt(child.getStartOffset(), templateLanguage);

      if (at instanceof XmlToken) {
        at = at.getParent();
      }

      // TODO: several comments
      if (at instanceof PsiComment &&
          at.getTextRange().equals(child.getTextRange()) &&
          at.getNode() != child) {
        return buildInjectedPsiBlocks(result, at.getNode(), wrap, alignment, indent);
      }
    }

    return false;
  }

  public boolean isCDATAStart() {
    return myNode.getElementType() == XmlTokenType.XML_CDATA_START;
  }

  public boolean isCDATAEnd() {
    return myNode.getElementType() == XmlTokenType.XML_CDATA_END;
  }

  public static boolean containsWhiteSpacesOnly(@NotNull ASTNode node) {
    PsiElement psiElement = node.getPsi();
    if (psiElement instanceof PsiWhiteSpace) return true;
    Language nodeLang = psiElement.getLanguage();
    if (!nodeLang.isKindOf(XMLLanguage.INSTANCE) ||
        isTextOnlyNode(node) ||
        node.getElementType() == XmlElementType.XML_PROLOG) {
      WhiteSpaceFormattingStrategy strategy = WhiteSpaceFormattingStrategyFactory.getStrategy(nodeLang);
      int length = node.getTextLength();
      return strategy.check(node.getChars(), 0, length) >= length;
    }
    return false;
  }

  private static boolean isTextOnlyNode(@NotNull ASTNode node) {
    if (node.getPsi() instanceof XmlText
        || node.getElementType() == XmlElementType.HTML_RAW_TEXT) {
      return true;
    }
    ASTNode firstChild = node.getFirstChildNode();
    ASTNode lastChild = node.getLastChildNode();
    if (firstChild != null && firstChild == lastChild && firstChild.getPsi() instanceof XmlText) {
      return true;
    }
    return false;
  }
}
