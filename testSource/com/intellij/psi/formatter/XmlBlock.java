package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.List;

public class XmlBlock implements Block{
  private final ASTNode myNode;

  private static final Key<Block> KEY = new Key<Block>("BLOCK");
  private static final Key<Wrap> WRAP_KEY = new Key<Wrap>("WRAP");
  private static final Key<Alignment> ALIGN_KEY = new Key<Alignment>("ALIGN");
  private List<Block> mySubBlocks;
  private final CodeStyleSettings mySettings;
  private String myText;

  public XmlBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, CodeStyleSettings settings) {
    myNode = node;
    myNode.putUserData(ALIGN_KEY, alignment);
    myNode.putUserData(WRAP_KEY, wrap);
    myNode.putUserData(KEY, this);
    mySettings = settings;
    myText = myNode.getText();
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
      final Wrap tagWrap = new Wrap(Wrap.Type.WRAP_ALWAYS);
      final Wrap attrWrap = new Wrap(getWrapType(mySettings.XML_ATTRIBUTE_WRAP));
      final Wrap textWrap = new Wrap(getWrapType(mySettings.XML_TEXT_WRAP));
      final Alignment attrAlignment = new Alignment(Alignment.Type.NORMAL);
      final Alignment textAlignment = new Alignment(Alignment.Type.NORMAL);
      while (child != null) {
        if (child.getElementType() != ElementType.WHITE_SPACE && child.getText().trim().length() > 0){
          Wrap wrap = chooseWrap(child, tagWrap, attrWrap, textWrap);
          Alignment alignment = chooseAlignment(child, attrAlignment, textAlignment);
          result.add(new XmlBlock(child, wrap, alignment, mySettings));
        }
        child = child.getTreeNext();
      }
    }
    return result;
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
    return myNode.getUserData(WRAP_KEY);
  }

  public Indent getChildIndent() {
    return myNode.getElementType() == ElementType.XML_TAG ? new Indent(Indent.Type.NORMAL, 1, 0) : null;
  }

  public Alignment getAlignment() {
    return myNode.getUserData(ALIGN_KEY);
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    final IElementType elementType = myNode.getElementType();
    final IElementType type1 = ((XmlBlock)child1).myNode.getElementType();
    final IElementType type2 = ((XmlBlock)child2).myNode.getElementType();
    if (elementType == ElementType.XML_TAG) {
      if (isXmlTagName(type1, type2)){
        final int spaces = mySettings.XML_SPACE_AROUND_TAG_NAME ? 1 : 0;
        return new SpaceProperty(spaces, spaces, 0, getMaxLine(), false);
      } else if (type2 == ElementType.XML_ATTRIBUTE) {
        return new SpaceProperty(1, 1, 0, getMaxLine(), false);
      }
    }
    if ((type2 == ElementType.XML_TAG || type2 == ElementType.XML_END_TAG_START || type2 == ElementType.XML_TEXT) && mySettings.XML_KEEP_WHITESPACES) {
      return new SpaceProperty(0,0,0,0,true);
    }
    if (type1 == ElementType.XML_DATA_CHARACTERS && type2 == ElementType.XML_DATA_CHARACTERS) {
      return new SpaceProperty(1, 1, 0, getMaxLine(), false);
    }
    if (type2 == ElementType.XML_TEXT && type1 == ElementType.XML_TAG) {
      return new SpaceProperty(0, 0, 1, getMaxLine(), false);
    }
    if (type1 == ElementType.XML_TEXT && type2 == ElementType.XML_END_TAG_START) {
      return new SpaceProperty(0, 0, 0, getMaxLine(), false);
    }

    if (type2 == ElementType.XML_TEXT && type1 == ElementType.XML_TAG_END) {
      return new SpaceProperty(0, 0, 0, getMaxLine(), false);
    }

    if (elementType == ElementType.XML_ATTRIBUTE) {
      if (type1 == ElementType.XML_EQ || type2 == ElementType.XML_EQ) {
        int spaces = mySettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE ? 1 : 0;
        return new SpaceProperty(spaces, spaces, 0, getMaxLine(), false);
      }
    }

    return new SpaceProperty(0, Integer.MAX_VALUE, 0, getMaxLine(), false);
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
    final ASTNode treeParent = myNode.getTreeParent();
    return treeParent == null ? null : treeParent.getUserData(KEY);
  }
}
