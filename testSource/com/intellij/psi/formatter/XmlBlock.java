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

public class XmlBlock implements Block{
  private final ASTNode myNode;

  private List<Block> mySubBlocks;
  private final CodeStyleSettings mySettings;

  private final Wrap myWrap;
  private final Alignment myAlignment;
  private final XmlBlock myParent;
  private final SpaceProperty myDefaultSpaceProperty;

  public XmlBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, CodeStyleSettings settings, XmlBlock parent) {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
    mySettings = settings;
    myParent = parent;
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
    if (myNode.getElementType() == ElementType.XML_TEXT && mySettings.XML_KEEP_WHITESPACES) {
      return result;
    }
    if (myNode instanceof CompositeElement) {
      ChameleonTransforming.transformChildren(myNode);
      ASTNode child = myNode.getFirstChildNode();
      final Formatter formatter = getFormatter();
      final Wrap tagWrap = formatter.createWrap(Wrap.Type.WRAP_ALWAYS, true);
      final Wrap attrWrap = formatter.createWrap(getWrapType(mySettings.XML_ATTRIBUTE_WRAP), false);
      final Wrap textWrap = formatter.createWrap(getWrapType(mySettings.XML_TEXT_WRAP), true);
      final Alignment attrAlignment = formatter.createAlignment();
      final Alignment textAlignment = formatter.createAlignment();
      while (child != null) {
        if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
          Wrap wrap = chooseWrap(child, tagWrap, attrWrap, textWrap);
          Alignment alignment = chooseAlignment(child, attrAlignment, textAlignment);
          result.add(new XmlBlock(child, wrap, alignment, mySettings, this));
        }
        child = child.getTreeNext();
      }
    }
    return result;
  }

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

  private Wrap.Type getWrapType(final int type) {
    if (type == CodeStyleSettings.DO_NOT_WRAP) return Wrap.Type.DO_NOT_WRAP;
    if (type == CodeStyleSettings.WRAP_ALWAYS) return Wrap.Type.WRAP_ALWAYS;
    if (type == CodeStyleSettings.WRAP_AS_NEEDED) return Wrap.Type.WRAP_AS_NEEDED;
    return Wrap.Type.CHOP_IF_NEEDED;
  }

  private Alignment chooseAlignment(final ASTNode child, final Alignment attrAlignment, final Alignment textAlignment) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return getAlignment();
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE && mySettings.XML_ALIGN_ATTRIBUTES) return attrAlignment;
    if (elementType == ElementType.XML_TEXT && mySettings.XML_ALIGN_TEXT) return textAlignment;
    return null;
  }

  private Wrap chooseWrap(final ASTNode child, final Wrap tagWrap, final Wrap attrWrap, final Wrap textWrap) {
    if (myNode.getElementType() == ElementType.XML_TEXT) return getWrap();
    final IElementType elementType = child.getElementType();
    if (elementType == ElementType.XML_ATTRIBUTE) return attrWrap;
    if (elementType == ElementType.XML_TAG) return tagWrap;
    if (elementType == ElementType.XML_END_TAG_START) {
      final PsiElement parent = SourceTreeToPsiMap.treeElementToPsi(child.getTreeParent());
      if ((parent instanceof XmlTag) && ((XmlTag)parent).getSubTags().length > 0) {
        return tagWrap;
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
    } else {
      return elementType == ElementType.XML_TAG ? formatter.getNormalIndent(1) : null;
    }
  }

  public Alignment getAlignment() {
    return myAlignment;
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    final IElementType elementType = myNode.getElementType();
    final IElementType type1 = ((XmlBlock)child1).myNode.getElementType();
    final IElementType type2 = ((XmlBlock)child2).myNode.getElementType();

    if ((type2 == ElementType.XML_TAG || type2 == ElementType.XML_END_TAG_START || type2 == ElementType.XML_TEXT) && mySettings.XML_KEEP_WHITESPACES) {
      return getFormatter().getReadOnlySpace();
    }

    if (elementType == ElementType.XML_TAG) {
      return getSpacesInsideTag(type1, type2);

    } else if (elementType == ElementType.XML_TEXT) {
      return getSpacesInsideText(type1, type2);

    } else if (elementType == ElementType.XML_ATTRIBUTE) {
      return getSpacesInsideAttribute(type1, type2);
    }

    return myDefaultSpaceProperty;
  }

  private Formatter getFormatter() {
    return Formatter.getInstance();
  }

  private SpaceProperty getSpacesInsideAttribute(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_EQ || type2 == ElementType.XML_EQ) {
      int spaces = mySettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE ? 1 : 0;
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
      final int spaces = mySettings.XML_SPACE_AROUND_TAG_NAME ? 1 : 0;
      return getFormatter().createSpaceProperty(spaces, spaces, 0, getMaxLine());
    } else if (type2 == ElementType.XML_ATTRIBUTE) {
      return getFormatter().createSpaceProperty(1, 1, 0, getMaxLine());
    } else if (type2 == ElementType.XML_TEXT && type1 == ElementType.XML_TAG) {
      return getFormatter().createSpaceProperty(0, 0, 1, getMaxLine());
    } else if (type1 == ElementType.XML_TEXT && type2 == ElementType.XML_END_TAG_START) {
      return getFormatter().createSpaceProperty(0, 0, 0, getMaxLine());
    } else if (type1 == ElementType.XML_TAG_END && type2 == ElementType.XML_END_TAG_START) {
      return getFormatter().createSpaceProperty(0, 0, 0, getMaxLine());
    } else if (type2 == ElementType.XML_TEXT && type1 == ElementType.XML_TAG_END) {
      return getFormatter().createSpaceProperty(0, 0, 0, getMaxLine());
    } else {
      return myDefaultSpaceProperty;
    }
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

  private int getMaxLine() {
    if (!mySettings.XML_KEEP_LINE_BREAKS) return 0;
    return mySettings.XML_KEEP_BLANK_LINES + 1;
  }

  public Block getParent() {
    return myParent;
  }

  public boolean skipIndent(final Block parent) {
    return myNode.getElementType() == ElementType.XML_COMMENT && myNode.textContains('\n');
  }
}
