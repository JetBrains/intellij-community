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

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SyntheticBlock extends AbstractSyntheticBlock implements Block, ReadOnlyBlockContainer {
  private final List<Block> mySubBlocks;
  private final Indent myChildIndent;

  public SyntheticBlock(final List<Block> subBlocks, final Block parent, final Indent indent, XmlFormattingPolicy policy, final Indent childIndent) {
    super(subBlocks, parent, policy, indent);
    mySubBlocks = subBlocks;
    myChildIndent = childIndent;
  }

  @NotNull
  public TextRange getTextRange() {
    return calculateTextRange(mySubBlocks);
  }

  @NotNull
  public List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  public Spacing getSpacing(Block child1, Block child2) {
    if (child1 instanceof ReadOnlyBlock || child2 instanceof ReadOnlyBlock) {
      return Spacing.getReadOnlySpacing();
    }
    if (!(child1 instanceof AbstractXmlBlock) || !(child2 instanceof AbstractXmlBlock)) {
      return null;
    }
    final ASTNode node1 = ((AbstractBlock)child1).getNode();
    final ASTNode node2 = ((AbstractBlock)child2).getNode();

    final IElementType type1 = node1.getElementType();
    final IElementType type2 = node2.getElementType();

    boolean firstIsText = isTextFragment(node1);
    boolean secondIsText = isTextFragment(node2);

    if (type1 == XmlElementType.XML_CDATA_START || type2 == XmlElementType.XML_CDATA_END) {
      if (myXmlFormattingPolicy.getKeepWhiteSpacesInsideCDATA()) {
        return Spacing.getReadOnlySpacing();
      }
      if (type1 == XmlElementType.XML_CDATA_START && type2 == XmlElementType.XML_CDATA_END) {
        return Spacing.createSpacing(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), 0); 
      }
      if (type1 == XmlElementType.XML_CDATA_START && child2 instanceof AnotherLanguageBlockWrapper ||
          type2 == XmlElementType.XML_CDATA_END && child1 instanceof AnotherLanguageBlockWrapper) {
        return Spacing.createSpacing(0, 0, 1, myXmlFormattingPolicy.getShouldKeepLineBreaks(), 0);
      }
    }

    boolean firstIsTag = node1.getPsi() instanceof XmlTag && !firstIsText;
    boolean secondIsTag = node2.getPsi() instanceof XmlTag && !secondIsText;

    boolean firstIsEntityRef = isEntityRef(node1);
    boolean secondIsEntityRef = isEntityRef(node2);

    if (isSpaceInText(firstIsTag, secondIsTag, firstIsText, secondIsText) && keepWhiteSpaces()) {
        return Spacing.getReadOnlySpacing();
    }

    if (firstIsEntityRef || secondIsEntityRef) {
      return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (type2 == XmlElementType.XML_EMPTY_ELEMENT_END && myXmlFormattingPolicy.addSpaceIntoEmptyTag()) {
      return Spacing.createSpacing(1, 1, 0,
                                   myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                   myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (secondIsTag && myXmlFormattingPolicy.keepWhiteSpacesInsideTag((XmlTag)node2.getPsi()) && node2.textContains('\n')) {
      return Spacing.getReadOnlySpacing();
    }

    if (isXmlTagName(type1, type2)){
      final int spaces = shouldAddSpaceAroundTagName(node1, node2) ? 1 : 0;
      return Spacing.createSpacing(spaces, spaces, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (type2 == XmlElementType.XML_ATTRIBUTE) {
      return Spacing.createSpacing(1, 1, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (((AbstractXmlBlock)child1).isTextElement() && ((AbstractXmlBlock)child2).isTextElement()) {
      return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaksInText(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (firstIsTag && insertLineFeedAfter((XmlTag)node1.getPsi())) {
      return Spacing.createSpacing(0, 0, 1, true, myXmlFormattingPolicy.getKeepBlankLines());
    }

    if ((firstIsText || firstIsTag) && secondIsTag) {
      //<tag/>text <tag/></tag>
      if (((AbstractXmlBlock)child2).insertLineBreakBeforeTag()) {
        return Spacing.createSpacing(0, Integer.MAX_VALUE, 2, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                     myXmlFormattingPolicy.getKeepBlankLines());
      } else if (((AbstractXmlBlock)child2).removeLineBreakBeforeTag()) {
        return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                     myXmlFormattingPolicy.getKeepBlankLines());
      }
    }

    final boolean saveSpacesBetweenTagAndText =
      myXmlFormattingPolicy.shouldSaveSpacesBetweenTagAndText() && child1.getTextRange().getEndOffset() < child2.getTextRange().getStartOffset();

    if (firstIsTag && secondIsText) {     //<tag/>-text

      if (((AbstractXmlBlock)child1).isTextElement() || saveSpacesBetweenTagAndText) {
        return Spacing.createSafeSpacing(true, myXmlFormattingPolicy.getKeepBlankLines());
      } else {
        return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
      }
    }

    if ( firstIsText && secondIsTag) {     //text-<tag/>
      if (((AbstractXmlBlock)child2).isTextElement() || saveSpacesBetweenTagAndText) {
        return Spacing.createSafeSpacing(true, myXmlFormattingPolicy.getKeepBlankLines());
      } else {
        return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
      }
    }

    if (firstIsTag && secondIsTag) {//<tag/><tag/>
      return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, true,
                                   myXmlFormattingPolicy.getKeepBlankLines());
    }

    return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, myXmlFormattingPolicy.getShouldKeepLineBreaksInText(), myXmlFormattingPolicy.getKeepBlankLines());
  }

  private boolean isEntityRef(final ASTNode node) {
    return node.getElementType() == XmlElementType.XML_ENTITY_REF;
  }

  private boolean shouldAddSpaceAroundTagName(final ASTNode node1, final ASTNode node2) {
    if (node1.getElementType() == XmlElementType.XML_START_TAG_START && node1.textContains('%')) return true;
    if (node2.getElementType() == XmlElementType.XML_EMPTY_ELEMENT_END && node2.textContains('%')) return true;
    return myXmlFormattingPolicy.getShouldAddSpaceAroundTagName();
  }

  private boolean isSpaceInText(final boolean firstIsTag,
                                final boolean secondIsTag,
                                final boolean firstIsText,
                                final boolean secondIsText) {
    return
      (firstIsText && secondIsText)
      || (firstIsTag && secondIsTag)
      || (firstIsTag && secondIsText)
      || (firstIsText && secondIsTag);
  }

  private boolean keepWhiteSpaces() {
    return (myXmlFormattingPolicy.keepWhiteSpacesInsideTag( getTag()) || myXmlFormattingPolicy.getShouldKeepWhiteSpaces());
  }

  protected boolean isTextFragment(final ASTNode node) {
    final ASTNode parent = node.getTreeParent();
    return parent != null && parent.getElementType() == XmlElementType.XML_TEXT
           || node.getElementType() == XmlElementType.XML_DATA_CHARACTERS
           
      ;
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (isOuterLanguageBlock()) return ChildAttributes.DELEGATE_TO_NEXT_CHILD;
    final List<Block> subBlocks = getSubBlocks();
    final int prevBlockIndex = newChildIndex - 1;
    if (prevBlockIndex >= 0 && prevBlockIndex < subBlocks.size()) {
      final Block prevBlock = subBlocks.get(newChildIndex - 1);
      if (isAttributeBlock(prevBlock)) {
        return new ChildAttributes(myChildIndent, prevBlock.getAlignment());
      }
    }
    return new ChildAttributes(myChildIndent, null);
  }

  private boolean isAttributeBlock(final Block block) {
    if (block instanceof XmlBlock) {
      return ((XmlBlock)block).getNode().getElementType() == XmlElementType.XML_ATTRIBUTE;
    }
    return false;
  }

  public boolean isIncomplete() {
    return getSubBlocks().get(getSubBlocks().size() - 1).isIncomplete();
  }

  public boolean endsWithAttribute() {
    return isAttributeBlock(getSubBlocks().get(getSubBlocks().size() - 1));
  }
}
