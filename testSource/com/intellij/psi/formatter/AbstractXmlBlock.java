package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.List;


public abstract class AbstractXmlBlock implements Block {
  protected final ASTNode myNode;
  private List<Block> mySubBlocks;
  private final Wrap myWrap;
  private final Alignment myAlignment;
  private final XmlBlock myParent;
  private final SpaceProperty myDefaultSpaceProperty;
  protected final CodeStyleSettings mySettings;

  public AbstractXmlBlock(final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          XmlBlock parent,
                          final CodeStyleSettings settings) {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
    myParent = parent;
    mySettings = settings;
    myDefaultSpaceProperty = getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, getMaxLine());
  }


  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = buildChildren();
    }
    return mySubBlocks;
  }

  private List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<Block>();
    if (myNode.getElementType() == ElementType.XML_TEXT) {
      if (getShouldKeepWhiteSpaces()) {
        return result;
      }

      if (keepWhiteSpacesInsideTag(getTag(myNode.getTreeParent()))) {
        return result;
      }
    }

    if (myNode instanceof CompositeElement) {
      ChameleonTransforming.transformChildren(myNode);
      ASTNode child = myNode.getFirstChildNode();
      final Formatter formatter = getFormatter();
      final Wrap attrWrap = formatter.createWrap(getWrapType(getAttributesWrap()), false);
      final Wrap textWrap = formatter.createWrap(getWrapType(getTextWrap()), true);
      final Wrap tagBeginWrap = createTagBeginWrapping(formatter);
      final Wrap tagEndWrap = getTagEndWrapping(formatter);
      final Alignment attrAlignment = formatter.createAlignment();
      final Alignment textAlignment = formatter.createAlignment();
      while (child != null) {
        if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){

          Wrap wrap = chooseWrap(child, tagBeginWrap, tagEndWrap, attrWrap, textWrap);
          Alignment alignment = chooseAlignment(child, attrAlignment, textAlignment);
          result.add(createChildBlock(child, wrap, alignment));
        }
        child = child.getTreeNext();
      }
    }
    return result;
  }

  private Wrap getTagEndWrapping(final Formatter formatter) {
    if (myNode.getElementType() == getTagType()) return formatter.createWrap(getWrappingTypeForTagEnd(getTag()), true);
    return null;
  }

  private Wrap createTagBeginWrapping(final Formatter formatter) {
    return formatter.createWrap(getWrappingTypeForTagBegin(), true);
  }

  protected abstract Block createChildBlock(final ASTNode child,
                                            final Wrap wrap,
                                            final Alignment alignment);

  private boolean containsWhiteSpacesOnly(final ASTNode node) {
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

  private int getWrapType(final int type) {
    if (type == CodeStyleSettings.DO_NOT_WRAP) return Wrap.NONE;
    if (type == CodeStyleSettings.WRAP_ALWAYS) return Wrap.ALWAYS;
    if (type == CodeStyleSettings.WRAP_AS_NEEDED) return Wrap.NORMAL;
    return Wrap.CHOP_DOWN_IF_LONG;
  }

  private Alignment chooseAlignment(final ASTNode child, final Alignment attrAlignment, final Alignment textAlignment) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return getAlignment();
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE && getShouldAlignAttributes()) return attrAlignment;
    if (elementType == ElementType.XML_TEXT && getShouldAlignText()) return textAlignment;
    return null;
  }

  private Wrap chooseWrap(final ASTNode child, final Wrap tagBeginWrap, final Wrap tagEndWrap, final Wrap attrWrap, final Wrap textWrap) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return getWrap();
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE) return attrWrap;
    if (elementType == getTagType()) return tagBeginWrap;
    if (elementType == ElementType.XML_END_TAG_START) {
      final PsiElement parent = SourceTreeToPsiMap.treeElementToPsi(child.getTreeParent());
      if ((parent instanceof XmlTag) && ((XmlTag)parent).getSubTags().length > 0) {
        return tagEndWrap;
      } else {
        return null;
      }
    }
    if (elementType == ElementType.XML_TEXT) return textWrap;
    return null;
  }

  public Wrap getWrap() {
    return myWrap;
  }

  public Indent getChildIndent() {
    final IElementType elementType = myNode.getElementType();
    final Formatter formatter = getFormatter();
    if (elementType == ElementType.XML_DOCUMENT || elementType == ElementType.XML_PROLOG) {
      return formatter.getNoneIndent();
    } else if (elementType == getTagType() && indentChildrenOf(getTag())){
      return formatter.getNormalIndent(1);
    } else {
      return null;
    }
  }

  public Alignment getAlignment() {
    return myAlignment;
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    final IElementType elementType = myNode.getElementType();
    final IElementType type1 = ((XmlBlock)child1).myNode.getElementType();
    final IElementType type2 = ((XmlBlock)child2).myNode.getElementType();

    if ((type2 == getTagType() || type2 == ElementType.XML_END_TAG_START || type2 == ElementType.XML_TEXT) && getShouldKeepWhiteSpaces()) {
      return getFormatter().getReadOnlySpace();
    }

    if (elementType == getTagType()) {
      return getSpacesInsideTag(type1, type2);

    } else if (elementType == ElementType.XML_TEXT) {
      return getSpacesInsideText(type1, type2);

    } else if (elementType == ElementType.XML_ATTRIBUTE) {
      return getSpacesInsideAttribute(type1, type2);
    }

    return myDefaultSpaceProperty;
  }

  protected Formatter getFormatter() {
    return Formatter.getInstance();
  }

  private SpaceProperty getSpacesInsideAttribute(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_EQ || type2 == ElementType.XML_EQ) {
      int spaces = getShouldAddSpaceAroundEqualityInAttribute() ? 1 : 0;
      return getFormatter().createSpaceProperty(spaces, spaces, 0, getMaxLine());
    } else {
      return myDefaultSpaceProperty;
    }
  }

  private SpaceProperty getSpacesInsideText(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_DATA_CHARACTERS && type2 == ElementType.XML_DATA_CHARACTERS) {
      return getFormatter().createSpaceProperty(1, 1, 0, getMaxLine());
    } else {
      return myDefaultSpaceProperty;
    }
  }

  private SpaceProperty getSpacesInsideTag(final IElementType type1, final IElementType type2) {
    if (isXmlTagName(type1, type2)){
      final int spaces = getShouldAddSpaceAroundTagName() ? 1 : 0;
      return getFormatter().createSpaceProperty(spaces, spaces, 0, getMaxLine());
    } else if (type2 == ElementType.XML_ATTRIBUTE) {
      return getFormatter().createSpaceProperty(1, 1, 0, getMaxLine());
    }

    if (keepWhiteSpacesInsideTag(getTag())) return getFormatter().getReadOnlySpace();

    if (type2 == ElementType.XML_TEXT && type1 == getTagType()) {
      return getFormatter().createSpaceProperty(0, 0, 1, getMaxLine());
    } else if (type1 == ElementType.XML_TEXT && type2 == ElementType.XML_END_TAG_START) {
      return getFormatter().createSpaceProperty(0, 0, 0, getMaxLine());
    } else if (type1 == ElementType.XML_TAG_END && type2 == ElementType.XML_END_TAG_START) {
      return getFormatter().createSpaceProperty(0, 0, 0, getMaxLine());
    } else if (type2 == ElementType.XML_TEXT && type1 == ElementType.XML_TAG_END) {
      return getFormatter().createSpaceProperty(0, 0, 0, getMaxLine());
    } else if (type2 == getTagType() && (type1 == ElementType.XML_TEXT || type1== getTagType()) && insertLineBreakBefore()) {
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 2, getMaxLine());
    } else if (type2 == getTagType() && (type1 == ElementType.XML_TEXT || type1== getTagType()) && removeLineBreakBefore()) {
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, 0);
    }
    else {
      return myDefaultSpaceProperty;
    }
  }

  private boolean removeLineBreakBefore() {
    return removeLineBreakBeforeTag(getTag());
  }

  private boolean isXmlTagName(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_START_TAG_START && type2 == ElementType.XML_NAME) return true;
    if (type1 == ElementType.XML_END_TAG_START && type2 == ElementType.XML_NAME) return true;
    if (type1 == ElementType.XML_NAME && type2 == ElementType.XML_TAG_END) return true;
    if (type1 == ElementType.XML_NAME && type2 == ElementType.XML_EMPTY_ELEMENT_END) return true;
    if (type1 == ElementType.XML_ATTRIBUTE && type2 == ElementType.XML_EMPTY_ELEMENT_END) return true;
    if (type1 == ElementType.XML_ATTRIBUTE && type2 == ElementType.XML_TAG_END) return true;
    return false;
  }

  protected int getMaxLine() {
    if (!getShouldKeepLineBreaks()) return 0;
    return getKeepBlankLines() + 1;
  }

  public Block getParent() {
    return myParent;
  }

  public boolean skipIndent(final Block parent) {
    return myNode.getElementType() == ElementType.XML_COMMENT && myNode.textContains('\n');
  }

  protected boolean insertLineBreakBefore() {
    return insertLineBreakBeforeTag(getTag());
  }

  private XmlTag getTag() {
    return getTag(myNode);
  }

  private XmlTag getTag(final ASTNode node) {
    return (XmlTag)SourceTreeToPsiMap.treeElementToPsi(node);
  }

  protected abstract int getWrappingTypeForTagEnd(final XmlTag xmlTag);

  protected abstract int getWrappingTypeForTagBegin();

  protected abstract boolean insertLineBreakBeforeTag(final XmlTag xmlTag);

  protected abstract boolean removeLineBreakBeforeTag(final XmlTag xmlTag);

  protected abstract boolean keepWhiteSpacesInsideTag(final XmlTag tag);

  protected abstract boolean indentChildrenOf(final XmlTag parentTag);

  protected abstract IElementType getTagType();

  protected abstract boolean isTextElement(XmlTag tag);

  protected abstract int getTextWrap();

  protected abstract int getAttributesWrap();

  protected abstract boolean getShouldAlignAttributes();

  protected abstract boolean getShouldAlignText();

  protected abstract boolean getShouldKeepWhiteSpaces();

  protected abstract boolean getShouldAddSpaceAroundEqualityInAttribute();

  protected abstract boolean getShouldAddSpaceAroundTagName();

  protected abstract int getKeepBlankLines();

  protected abstract boolean getShouldKeepLineBreaks();
}
