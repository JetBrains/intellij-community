package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.List;

public class XmlBlock extends AbstractXmlBlock {

  public XmlBlock(final ASTNode node,
                 final Wrap wrap,
                 final Alignment alignment,
                 AbstractXmlBlock parent,
                 final XmlFormattingPolicy policy) {
    super(node, wrap, alignment, parent, policy);
  }

  protected List<Block> buildChildren() {

    final ArrayList<Block> result = new ArrayList<Block>();

    if (myNode.getElementType() == ElementType.XML_TEXT) {
      if (myXmlFormattingPolicy.getShouldKeepWhiteSpaces()) {
        return result;
      }

      final ASTNode treeParent = myNode.getTreeParent();
      if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(getTag(treeParent))) {
        return result;
      }
    }

    if (myNode instanceof CompositeElement) {
      ChameleonTransforming.transformChildren(myNode);
      ASTNode child = myNode.getFirstChildNode();
      while (child != null) {
        if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
          result.add(createChildBlock(child, getChildWrap(), getChildAlignment()));
        }
        child = child.getTreeNext();
      }
    }
    return result;
  }

  private Alignment getChildAlignment() {
    if (myNode.getElementType() == ElementType.XML_TEXT) {
      return getAlignment();
    }
    return null;
  }

  private Wrap getChildWrap() {
    if (myNode.getElementType() == ElementType.XML_TEXT) {
      return getWrap();
    }
    return null;
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    final IElementType elementType = myNode.getElementType();
    final IElementType type1 = ((AbstractXmlBlock)child1).myNode.getElementType();
    final IElementType type2 = ((AbstractXmlBlock)child2).myNode.getElementType();

    if ((type2 == getTagType() || type2 == ElementType.XML_END_TAG_START || type2 == ElementType.XML_TEXT) && myXmlFormattingPolicy.getShouldKeepWhiteSpaces()) {
      return getFormatter().getReadOnlySpace();
    }

    if (elementType == ElementType.XML_TEXT) {
      return getSpacesInsideText(type1, type2);

    } else if (elementType == ElementType.XML_ATTRIBUTE) {
      return getSpacesInsideAttribute(type1, type2);
    }

    if (type1 == ElementType.XML_PROLOG) {
      return createDefaultSpace(1);
    }

    return createDefaultSpace(0);
  }

  private SpaceProperty getSpacesInsideAttribute(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_EQ || type2 == ElementType.XML_EQ) {
      int spaces = myXmlFormattingPolicy.getShouldAddSpaceAroundEqualityInAttribute() ? 1 : 0;
      return getFormatter().createSpaceProperty(spaces, spaces, 0, getMaxLine(0));
    } else {
      return createDefaultSpace(0);
    }
  }

  private SpaceProperty getSpacesInsideText(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_DATA_CHARACTERS && type2 == ElementType.XML_DATA_CHARACTERS) {
      return getFormatter().createSpaceProperty(1, 1, 0,getMaxLine(0));
    } else {
      return createDefaultSpace(0);
    }
  }

  public Indent getIndent() {
    if (myNode.getElementType() == ElementType.XML_TEXT || myNode.getElementType() == ElementType.XML_PROLOG) {
      return getFormatter().getNoneIndent();
    } else if (myNode.getElementType() == ElementType.XML_COMMENT && myNode.textContains('\n')){
      return getFormatter().getStartOfLineAlignment();      
    }
    else {
      return null;
    }
  }

  public boolean insertLineBreakBeforeTag() {
    return false;
  }

  public boolean removeLineBreakBeforeTag() {
    return false;
  }

  public boolean isTextElement() {
    return myNode.getElementType() == ElementType.XML_TEXT || myNode.getElementType() == ElementType.XML_DATA_CHARACTERS;
  }

}
