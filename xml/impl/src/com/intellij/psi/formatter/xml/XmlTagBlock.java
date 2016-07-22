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

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class XmlTagBlock extends AbstractXmlBlock{
  private final Indent myIndent;

  public XmlTagBlock(final ASTNode node,
                       final Wrap wrap,
                       final Alignment alignment,
                       final XmlFormattingPolicy policy,
                       final Indent indent) {
      super(node, wrap, alignment, policy, false);
      myIndent = indent;
  }

  public XmlTagBlock(final ASTNode node,
                     final Wrap wrap,
                     final Alignment alignment,
                     final XmlFormattingPolicy policy,
                     final Indent indent,
                     final boolean preserveSpace) {
    super(node, wrap, alignment, policy, preserveSpace);
    myIndent = indent;
  }

  @Override
  protected List<Block> buildChildren() {
    ASTNode child = myNode.getFirstChildNode();
    final Wrap attrWrap = Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getAttributesWrap()), false);
    final Wrap textWrap = Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getTextWrap(getTag())), true);
    final Wrap tagBeginWrap = createTagBeginWrapping(getTag());
    final Alignment attrAlignment = Alignment.createAlignment();
    final Alignment textAlignment = Alignment.createAlignment();
    final ArrayList<Block> result = new ArrayList<>(3);
    ArrayList<Block> localResult = new ArrayList<>(1);

    boolean insideTag = true;

    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){

        Wrap wrap = chooseWrap(child, tagBeginWrap, attrWrap, textWrap);
        Alignment alignment = chooseAlignment(child, attrAlignment, textAlignment);

        if (child.getElementType() == XmlTokenType.XML_TAG_END) {
          child = processChild(localResult,child, wrap, alignment, myXmlFormattingPolicy.getTagEndIndent());
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<>(1);
          insideTag = true;
        }
        else if (child.getElementType() == XmlTokenType.XML_START_TAG_START) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
          }
          localResult = new ArrayList<>(1);
          child = processChild(localResult,child, wrap, alignment, null);
        }
        else if (child.getElementType() == XmlTokenType.XML_END_TAG_START) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
            localResult = new ArrayList<>(1);
          }
          child = processChild(localResult,child, wrap, alignment, null);
        } else if (child.getElementType() == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          child = processChild(localResult,child, wrap, alignment, myXmlFormattingPolicy.getTagEndIndent());
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<>(1);
        }
        else if (isJspxJavaContainingNode(child)) {
          createJspTextNode(localResult, child, getChildIndent());
        }
        /*
        else if (child.getElementType() == ElementType.XML_TEXT) {
          child  = createXmlTextBlocks(localResult, child, wrap, alignment);
        }
        */
        else {
          final Indent indent;

          if (isJspResult(localResult)) {
            //indent = FormatterEx.getInstance().getNoneIndent();
            indent = getChildrenIndent();
          } else if (!insideTag) {
            indent = null;
          }
          else {
            indent = getChildrenIndent();
          }

          child = processChild(localResult,child, wrap, alignment, indent);
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }

    if (!localResult.isEmpty()) {
      result.add(createTagContentNode(localResult));
    }

    return result;

  }

  protected boolean isJspResult(final ArrayList<Block> localResult) {
    return false;
  }

  @Override
  @Nullable
  protected
  ASTNode processChild(List<Block> result, final ASTNode child, final Wrap wrap, final Alignment alignment, final Indent indent) {
    IElementType type = child.getElementType();
    if (type == XmlElementType.XML_TEXT) {
      final PsiElement parent = child.getPsi().getParent();

      if (parent instanceof XmlTag && ((XmlTag)parent).getSubTags().length == 0) {
        if (buildInjectedPsiBlocks(result, child, wrap, alignment, indent)) return child;
      }
      return createXmlTextBlocks(result, child, wrap, alignment);
    } else if (type == XmlElementType.XML_COMMENT) {
      if (buildInjectedPsiBlocks(result, child, wrap, alignment, indent)) return child;
      return super.processChild(result, child, wrap, alignment, indent);
    }
    else {
      return super.processChild(result, child, wrap, alignment, indent);
    }
  }

  protected Indent getChildrenIndent() {
    return myXmlFormattingPolicy.indentChildrenOf(getTag())
           ? Indent.getNormalIndent()
           : Indent.getNoneIndent();
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  private ASTNode createXmlTextBlocks(final List<Block> list, final ASTNode textNode, final Wrap wrap, final Alignment alignment) {
    ASTNode child = textNode.getFirstChildNode();
    return createXmlTextBlocks(list, textNode, child, wrap, alignment);
  }

  private ASTNode createXmlTextBlocks(final List<Block> list, final ASTNode textNode, ASTNode child,
                                      final Wrap wrap,
                                      final Alignment alignment
  ) {
    while (child != null) {
      if (!AbstractXmlBlock.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        final Indent indent = getChildrenIndent();
        child = processChild(list,child,  wrap, alignment, indent);
        if (child == null) return child;
        if (child.getTreeParent() != textNode) {
          if (child.getTreeParent() != myNode) {
            return createXmlTextBlocks(list, child.getTreeParent(), child.getTreeNext(), wrap, alignment);
          } else {
            return child;
          }
        }
      }
      child = child.getTreeNext();
    }
    return textNode;
  }

  private Block createTagContentNode(final ArrayList<Block> localResult) {
    return createSyntheticBlock(localResult, getChildrenIndent());
  }

  protected Block createSyntheticBlock(final ArrayList<Block> localResult, final Indent childrenIndent) {
    return new SyntheticBlock(localResult, this, Indent.getNoneIndent(), myXmlFormattingPolicy, childrenIndent);
  }

  private Block createTagDescriptionNode(final ArrayList<Block> localResult) {
    return createSyntheticBlock(localResult, null);
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    if (isPreserveSpace()) return Spacing.getReadOnlySpacing();
    if(child1 instanceof AbstractSyntheticBlock && child2 instanceof AbstractSyntheticBlock) {
      return getSpacing((AbstractSyntheticBlock)child1, (AbstractSyntheticBlock)child2);
    }
    return null;
  }

  protected Spacing getSpacing(final AbstractSyntheticBlock syntheticBlock1, final AbstractSyntheticBlock syntheticBlock2) {
    if (syntheticBlock2.startsWithCDATA() || syntheticBlock1.endsWithCDATA()) {
      return Spacing.getReadOnlySpacing();
    }

    if (syntheticBlock1.containsCDATA() && syntheticBlock2.isTagDescription()
        || syntheticBlock1.isTagDescription() && syntheticBlock2.containsCDATA()) {
      int lineFeeds = 0;
      switch(myXmlFormattingPolicy.getWhiteSpaceAroundCDATAOption()) {
        case XmlCodeStyleSettings.WS_AROUND_CDATA_NONE:
          break;
        case XmlCodeStyleSettings.WS_AROUND_CDATA_NEW_LINES:
          lineFeeds = 1;
          break;
        case XmlCodeStyleSettings.WS_AROUND_CDATA_PRESERVE:
          return Spacing.getReadOnlySpacing();
        default:
          assert false : "Unexpected whitespace around CDATA code style option.";
      }
      return Spacing.createSpacing(0, 0, lineFeeds, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                   myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (syntheticBlock2.isJspTextBlock() || syntheticBlock1.isJspTextBlock()) {
      return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (syntheticBlock2.isJspxTextBlock() || syntheticBlock1.isJspxTextBlock()) {
      return Spacing.createSpacing(0, 0, 1, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(getTag())) return Spacing.getReadOnlySpacing();

    if (myXmlFormattingPolicy.getShouldKeepWhiteSpaces()) {
      return Spacing.getReadOnlySpacing();
    }

    if (syntheticBlock2.startsWithTag() ) {
      final XmlTag startTag = syntheticBlock2.getStartTag();
      if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(startTag) && startTag.textContains('\n')) {
        return getChildrenIndent() != Indent.getNoneIndent() ? Spacing.getReadOnlySpacing():Spacing.createSpacing(0,0,0,true,myXmlFormattingPolicy.getKeepBlankLines());
      }
    }

    boolean saveSpacesBetweenTagAndText = myXmlFormattingPolicy.shouldSaveSpacesBetweenTagAndText() &&
      syntheticBlock1.getTextRange().getEndOffset() < syntheticBlock2.getTextRange().getStartOffset();

    if (syntheticBlock1.endsWithTextElement() && syntheticBlock2.startsWithTextElement()) {
      return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaksInText(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (syntheticBlock1.endsWithText()) { //text</tag
      if (syntheticBlock1.insertLineFeedAfter()) {
        return Spacing.createDependentLFSpacing(0, 0, getTag().getTextRange(), myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
      }
      if (saveSpacesBetweenTagAndText) {
        return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
      }
      return Spacing.createSpacing(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());

    } else if (syntheticBlock1.isTagDescription() && syntheticBlock2.isTagDescription()) { //></
      return Spacing.createSpacing(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock2.startsWithText()) { //>text
      if (saveSpacesBetweenTagAndText) {
        return Spacing.createSafeSpacing(true, myXmlFormattingPolicy.getKeepBlankLines());
      }
      return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock1.isTagDescription() && syntheticBlock2.startsWithTag()) {
      return Spacing.createSpacing(0, 0, myXmlFormattingPolicy.insertLineBreakAfterTagBegin(getTag()) ?  2 : 0,
                                   true, myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock1.insertLineFeedAfter()) {
      return Spacing.createSpacing(0,0,1,true,myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock1.endsWithTag() && syntheticBlock2.isTagDescription()) {
      return Spacing.createSpacing(0, 0, myXmlFormattingPolicy.insertLineBreakAfterTagBegin(getTag()) ? 2 : 0,
                                   true, myXmlFormattingPolicy.getKeepBlankLines());
    } else {
      return createDefaultSpace(true, true);
    }
  }

  @Override
  public boolean insertLineBreakBeforeTag() {
    return myXmlFormattingPolicy.insertLineBreakBeforeTag(getTag());
  }

  @Override
  public int getBlankLinesBeforeTag() {
    return myXmlFormattingPolicy.getBlankLinesBeforeTag(getTag());
  }

  @Override
  public boolean removeLineBreakBeforeTag() {
    return myXmlFormattingPolicy.removeLineBreakBeforeTag(getTag());
  }

  @Override
  public boolean isTextElement() {
    return myXmlFormattingPolicy.isTextElement(getTag());
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (isAfterAttribute(newChildIndex)) {
      List<Block> subBlocks = getSubBlocks();
      Block subBlock = subBlocks.get(newChildIndex - 1);
      int prevSubBlockChildrenCount = subBlock.getSubBlocks().size();
      return subBlock.getChildAttributes(prevSubBlockChildrenCount);
    }
    else {
      if (myXmlFormattingPolicy.indentChildrenOf(getTag())) {
        return new ChildAttributes(Indent.getNormalIndent(), null);
      } else {
        return new ChildAttributes(Indent.getNoneIndent(), null);
      }
    }
  }

  private boolean isAfterAttribute(final int newChildIndex) {
    List<Block> subBlocks = getSubBlocks();
    int index = newChildIndex - 1;
    Block prevBlock = index < subBlocks.size() ? subBlocks.get(index):null;
    return prevBlock instanceof SyntheticBlock && ((SyntheticBlock)prevBlock).endsWithAttribute();
  }
}
