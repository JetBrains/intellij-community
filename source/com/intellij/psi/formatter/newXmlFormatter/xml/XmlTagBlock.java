package com.intellij.psi.formatter.newXmlFormatter.xml;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;

import java.util.ArrayList;
import java.util.List;

public class XmlTagBlock extends AbstractXmlBlock{
  public XmlTagBlock(final ASTNode node,
                     final Wrap wrap,
                     final Alignment alignment,
                     final XmlFormattingPolicy policy) {
    super(node, wrap, alignment, policy);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();
    final Formatter formatter = getFormatter();
    final Wrap attrWrap = formatter.createWrap(getWrapType(myXmlFormattingPolicy.getAttributesWrap()), false);
    final Wrap textWrap = formatter.createWrap(getWrapType(myXmlFormattingPolicy.getTextWrap()), true);
    final Wrap tagBeginWrap = createTagBeginWrapping(formatter);
    final Alignment attrAlignment = formatter.createAlignment();
    final Alignment textAlignment = formatter.createAlignment();
    final ArrayList<Block> result = new ArrayList<Block>();
    ArrayList<Block> localResult = new ArrayList<Block>();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){

        Wrap wrap = chooseWrap(child, tagBeginWrap, attrWrap, textWrap);
        Alignment alignment = chooseAlignment(child, attrAlignment, textAlignment);
        if (child.getElementType() == ElementType.XML_TAG_END) {
          localResult.add(createChildBlock(child, wrap, alignment));
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<Block>();
        } else if (child.getElementType() == ElementType.XML_END_TAG_START) {
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
            localResult = new ArrayList<Block>();
          }
          localResult.add(createChildBlock(child, wrap, alignment));
        } else if (child.getElementType() == ElementType.XML_EMPTY_ELEMENT_END) {
          localResult.add(createChildBlock(child, wrap, alignment));
          result.add(createTagDescriptionNode(localResult));
        } else if (child.getElementType() == ElementType.XML_TEXT) {
          createXmlTextBlocks(localResult, child, wrap, alignment);
        }
        else {
          localResult.add(createChildBlock(child, wrap, alignment));
        }
      }
      child = child.getTreeNext();
    }
    return result;

  }

  private void createXmlTextBlocks(final ArrayList<Block> list, final ASTNode textNode, final Wrap wrap, final Alignment alignment) {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = textNode.getFirstChildNode();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        list.add(createChildBlock(child,  wrap, alignment));
      }
      child = child.getTreeNext();
    }
  }

  private Block createTagContentNode(final ArrayList<Block> localResult) {
    return AbstractSynteticBlock.createSynteticBlock(localResult, this, myXmlFormattingPolicy.indentChildrenOf(getTag()) ?
           getFormatter().createNormalIndent() : getFormatter().getNoneIndent(), myXmlFormattingPolicy);
  }

  private Block createTagDescriptionNode(final ArrayList<Block> localResult) {
    return AbstractSynteticBlock.createSynteticBlock(localResult, this, null, myXmlFormattingPolicy);
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    final AbstractSynteticBlock synteticBlock1 = ((AbstractSynteticBlock)child1);
    final AbstractSynteticBlock synteticBlock2 = ((AbstractSynteticBlock)child2);

    if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(getTag())) return getFormatter().getReadOnlySpace();

    if (myXmlFormattingPolicy.getShouldKeepWhiteSpaces()) {
      return getFormatter().getReadOnlySpace();
    }

    if (synteticBlock1.endsWithTextElement() && synteticBlock2.startsWithTextElement()) {
      return getFormatter().createSafeSpace(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (synteticBlock1.endsWithText()) { //text</tag
      return getFormatter().createSpaceProperty(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    } else if (synteticBlock1.isTagDescription() && synteticBlock2.isTagDescription()) { //></
      return getFormatter().createSpaceProperty(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    } else if (synteticBlock2.startsWithText()) { //>text
      return getFormatter().createSpaceProperty(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    }
    else {
      return createDefaultSpace(true);
    }

  }

  public Indent getIndent() {
    return getFormatter().getNoneIndent();
  }

  public boolean insertLineBreakBeforeTag() {
    return myXmlFormattingPolicy.insertLineBreakBeforeTag(getTag());
  }

  public boolean removeLineBreakBeforeTag() {
    return myXmlFormattingPolicy.removeLineBreakBeforeTag(getTag());
  }

  public boolean isTextElement() {
    return myXmlFormattingPolicy.isTextElement(getTag());
  }

  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(Formatter.getInstance().createNormalIndent(), null);
  }
}
