/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.formatting.*;
import com.intellij.lang.*;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyFactory;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public abstract class AbstractXmlBlock extends AbstractBlock {
  protected XmlFormattingPolicy myXmlFormattingPolicy;
  protected final XmlInjectedLanguageBlockBuilder myInjectedBlockBuilder;
  private final boolean myPreserveSpace;

  protected AbstractXmlBlock(final ASTNode node,
                            final Wrap wrap,
                            final Alignment alignment,
                            final XmlFormattingPolicy policy) {
    this(node, wrap, alignment, policy, false);
  }


  protected AbstractXmlBlock(final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          final XmlFormattingPolicy policy,
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
   *         contains 'default'. If the attribute is not defined, return the current value.
   */ 
  private static boolean shouldPreserveSpace(ASTNode node, boolean defaultValue) {
    if (node.getPsi() instanceof XmlTag) {
      XmlTag tag = (XmlTag)node.getPsi();
      if (tag != null) {
        XmlAttribute spaceAttr = tag.getAttribute("xml:space");
        if (spaceAttr != null) {
          String value = spaceAttr.getValue();
          if ("preserve".equals(value)) {
            return true;
          }
          if ("default".equals(value))  {
            return false;
          }
        }
      }
    }
    return defaultValue;
  }
  
  public boolean isPreserveSpace() {
    return myPreserveSpace;
  }


  public static WrapType getWrapType(final int type) {
    if (type == CommonCodeStyleSettings.DO_NOT_WRAP) return WrapType.NONE;
    if (type == CommonCodeStyleSettings.WRAP_ALWAYS) return WrapType.ALWAYS;
    if (type == CommonCodeStyleSettings.WRAP_AS_NEEDED) return WrapType.NORMAL;
    return WrapType.CHOP_DOWN_IF_LONG;
  }

  protected Alignment chooseAlignment(final ASTNode child, final Alignment attrAlignment, final Alignment textAlignment) {
    if (myNode.getElementType() == XmlElementType.XML_TEXT) return getAlignment();
    final IElementType elementType = child.getElementType();
    if (elementType == XmlElementType.XML_ATTRIBUTE && myXmlFormattingPolicy.getShouldAlignAttributes()) return attrAlignment;
    if (elementType == XmlElementType.XML_TEXT && myXmlFormattingPolicy.getShouldAlignText()) return textAlignment;
    return null;
  }

  private Wrap getTagEndWrapping(final XmlTag parent) {
    return Wrap.createWrap(myXmlFormattingPolicy.getWrappingTypeForTagEnd(parent), true);
  }

  protected Wrap chooseWrap(final ASTNode child, final Wrap tagBeginWrap, final Wrap attrWrap, final Wrap textWrap) {
    if (myNode.getElementType() == XmlElementType.XML_TEXT) return textWrap;
    final IElementType elementType = child.getElementType();
    if (elementType == XmlElementType.XML_ATTRIBUTE) return attrWrap;
    if (elementType == XmlTokenType.XML_START_TAG_START) return tagBeginWrap;
    if (elementType == XmlTokenType.XML_END_TAG_START) {
      final PsiElement parent = SourceTreeToPsiMap.treeElementToPsi(child.getTreeParent());
      if (parent instanceof XmlTag) {
        final XmlTag tag = (XmlTag)parent;
        if (canWrapTagEnd(tag)) {
          return getTagEndWrapping(tag);
        }
      }
      return null;
    }
    if (elementType == XmlElementType.XML_TEXT || elementType == XmlTokenType.XML_DATA_CHARACTERS) return textWrap;
    return null;
  }

  protected boolean canWrapTagEnd(final XmlTag tag) {
    return tag.getSubTags().length > 0;
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

  @Nullable
  protected
  ASTNode processChild(List<Block> result,
                       final ASTNode child,
                       final Wrap wrap,
                       final Alignment alignment,
                       final Indent indent) {
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
    final XmlTag[] subTags = getSubTags();
    for (XmlTag subTag : subTags) {
      final TextRange subTagRange = subTag.getTextRange();
      if (subTagRange.getEndOffset() < tagRange.getStartOffset()) continue;
      if (subTagRange.getStartOffset() > tagRange.getEndOffset()) return true;

      if (tagRange.getStartOffset() > subTagRange.getStartOffset() && tagRange.getEndOffset() < subTagRange.getEndOffset()) return false;
      if (tagRange.getEndOffset() > subTagRange.getStartOffset() && tagRange.getEndOffset() < subTagRange.getEndOffset()) return false;

    }
    return true;
  }

  private XmlTag[] getSubTags() {

    if (myNode instanceof XmlTag) {
      return ((XmlTag)myNode.getPsi()).getSubTags();
    }
    else if (myNode.getPsi() instanceof XmlElement) {
      return collectSubTags((XmlElement)myNode.getPsi());
    }
    else {
      return XmlTag.EMPTY;
    }

  }

  private static XmlTag[] collectSubTags(final XmlElement node) {
    final List<XmlTag> result = new ArrayList<>();
    node.processElements(new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull final PsiElement element) {
        if (element instanceof XmlTag) {
          result.add((XmlTag)element);
        }
        return true;
      }
    }, node);
    return result.toArray(new XmlTag[result.size()]);
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
                                         @NotNull final ASTNode child,
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

  protected void processSimpleChild(final ASTNode child,
                                  final Indent indent,
                                  final List<Block> result,
                                  final Wrap wrap,
                                  final Alignment alignment) {
    if (isXmlTag(child)) {
      result.add(createTagBlock(child, indent != null ? indent : Indent.getNoneIndent(), wrap, alignment));
    } else if (child.getElementType() == XmlElementType.XML_DOCTYPE) {
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
    else {
      result.add(createSimpleChild(child, indent, wrap, alignment));
    }
  }


  protected XmlBlock createSimpleChild(final ASTNode child, final Indent indent, final Wrap wrap, final Alignment alignment) {
    return new XmlBlock(child, wrap, alignment, myXmlFormattingPolicy, indent, null, isPreserveSpace());
  }

  protected XmlTagBlock createTagBlock(final ASTNode child, final Indent indent, final Wrap wrap, final Alignment alignment) {
    return new XmlTagBlock(child, wrap, alignment, myXmlFormattingPolicy, indent != null ? indent : Indent.getNoneIndent(), isPreserveSpace());
  }

  @Nullable
  protected XmlTag findXmlTagAt(final ASTNode child, final int startOffset) {
    return null; 
  }

  @Nullable
  protected ASTNode createAnotherTreeNode(final List<Block> result,
                                            final ASTNode child,
                                            PsiElement tag,
                                            final Indent indent,
                                            final Wrap wrap, final Alignment alignment) {
    return null;
  }

  @Nullable
  protected Block createAnotherTreeTagBlock(final PsiElement tag, final Indent childIndent) {
    return null;
  }

  protected XmlFormattingPolicy createPolicyFor() {
    return myXmlFormattingPolicy;
  }

  @Nullable
  protected XmlTag getAnotherTreeTag(final ASTNode child) {
    return null;

  }
  protected boolean isXmlTag(final ASTNode child) {
    return isXmlTag(child.getPsi());
  }

  protected boolean isXmlTag(final PsiElement psi) {
    return psi instanceof XmlTag;
  }

  protected boolean useMyFormatter(final Language myLanguage, final Language childLanguage, final PsiElement childPsi) {
    if (myLanguage == childLanguage ||
        childLanguage == StdFileTypes.HTML.getLanguage() ||
        childLanguage == StdFileTypes.XHTML.getLanguage() ||
        childLanguage == StdFileTypes.XML.getLanguage()) {
      return true;
    }
    final FormattingModelBuilder childFormatter = LanguageFormatting.INSTANCE.forLanguage(childLanguage);
    return childFormatter == null ||
           childFormatter instanceof DelegatingFormattingModelBuilder &&
           ((DelegatingFormattingModelBuilder)childFormatter).dontFormatMyModel();
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

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.xml.AbstractXmlBlock");

  protected void createJspTextNode(final List<Block> localResult, final ASTNode child, final Indent indent) {
  }

  @Nullable
  protected static ASTNode findChildAfter(@NotNull final ASTNode child, final int endOffset) {
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

  private static  boolean isComment(final ASTNode node) {
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
    if (node.getPsi() instanceof XmlText) return true;
    ASTNode firstChild = node.getFirstChildNode();
    ASTNode lastChild = node.getLastChildNode();
    if (firstChild != null && firstChild == lastChild && firstChild.getPsi() instanceof XmlText) {
      return true;
    }
    return false;
  }

}
