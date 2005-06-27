package com.intellij.psi.formatter.xml;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.PsiFile;
import com.intellij.codeFormatting.general.FormatterUtil;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

public class XmlBlock extends AbstractXmlBlock {
  private final Indent myIndent;

  public XmlBlock(final ASTNode node,
                  final Wrap wrap,
                  final Alignment alignment,
                  final XmlFormattingPolicy policy, final Indent indent) {
    super(node, wrap, alignment, policy);
    myIndent = indent;
  }

  protected List<Block> buildChildren() {

    final ArrayList<Block> result = new ArrayList<Block>();

    if (myNode.getElementType() == ElementType.XML_ATTRIBUTE_VALUE) {
      return result;
    }

    if (myNode.getElementType() == ElementType.XML_TEXT) {
      if (myXmlFormattingPolicy.getShouldKeepWhiteSpaces()) {
        return result;
      }

      final ASTNode treeParent = myNode.getTreeParent();
      final XmlTag tag = getTag(treeParent);
      if (tag != null) {
        if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(tag)) {
          return result;
        }
      }
    }

    if (myNode instanceof CompositeElement) {
      ChameleonTransforming.transformChildren(myNode);
      ASTNode child = myNode.getFirstChildNode();
      while (child != null) {
        if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
          result.add(createChildBlock(child, null, null, getChildDefaultIndent()));
        }
        child = child.getTreeNext();
      }
    }
    return result;
  }

  private Indent getChildDefaultIndent() {
    if (myNode.getElementType() == ElementType.HTML_DOCUMENT) {
      return Formatter.getInstance().getNoneIndent();
    }
    if (myNode.getElementType() == ElementType.DUMMY_HOLDER) {
      return Formatter.getInstance().getNoneIndent();
    }
    else {
      return null;
    }
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    if (!(child1 instanceof AbstractBlock) || !(child2 instanceof AbstractBlock)) {
      return null;
    }

    final IElementType elementType = myNode.getElementType();
    final IElementType type1 = ((AbstractBlock)child1).getNode().getElementType();
    final IElementType type2 = ((AbstractBlock)child2).getNode().getElementType();

    if ((type2 == getTagType() || type2 == ElementType.XML_END_TAG_START || type2 == ElementType.XML_TEXT) && myXmlFormattingPolicy
      .getShouldKeepWhiteSpaces()) {
      return getFormatter().getReadOnlySpace();
    }

    if (elementType == ElementType.XML_TEXT) {
      return getSpacesInsideText(type1, type2);

    }
    else if (elementType == ElementType.XML_ATTRIBUTE) {
      return getSpacesInsideAttribute(type1, type2);
    }

    if (type1 == ElementType.XML_PROLOG) {
      return createDefaultSpace(true);
    }

    if (elementType == ElementType.XML_DOCTYPE) {
      return createDefaultSpace(true);
    }

    return createDefaultSpace(false);
  }

  private SpaceProperty getSpacesInsideAttribute(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_EQ || type2 == ElementType.XML_EQ) {
      int spaces = myXmlFormattingPolicy.getShouldAddSpaceAroundEqualityInAttribute() ? 1 : 0;
      return getFormatter().createSpaceProperty(spaces, spaces, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                                myXmlFormattingPolicy.getKeepBlankLines());
    }
    else {
      return createDefaultSpace(false);
    }
  }

  private SpaceProperty getSpacesInsideText(final IElementType type1, final IElementType type2) {
    if (type1 == ElementType.XML_DATA_CHARACTERS && type2 == ElementType.XML_DATA_CHARACTERS) {
      return getFormatter().createSpaceProperty(1, 1, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                                myXmlFormattingPolicy.getKeepBlankLines());
    }
    else {
      return createDefaultSpace(false);
    }
  }

  public Indent getIndent() {
    if (myNode.getElementType() == ElementType.XML_PROLOG
        || myNode.getElementType() == ElementType.XML_DOCTYPE
        || SourceTreeToPsiMap.treeElementToPsi(myNode) instanceof XmlDocument) {
      return getFormatter().getNoneIndent();
    }
    else if (myNode.getElementType() == ElementType.XML_COMMENT && myNode.textContains('\n')) {
      return getFormatter().createAbsoluteNoneIndent();
    }

    /*
    if (myNode.getElementType() == ElementType.JSP_SCRIPTLET || myNode.getElementType() == ElementType.JSP_DECLARATION) {
      return getFormatter().getNoneIndent();
    }
    if (myNode.getElementType() == ElementType.JSP_XML_TEXT) {
      return getFormatter().createNormalIndent();
    }
    if (myNode.getElementType() == ElementType.XML_TEXT) {
      if (myNode.getPsi().getParent() instanceof JspXmlRootTag) {
        return getFormatter().getNoneIndent();
      } else {
        return null;
      }
    }
    else if (myNode.getElementType() == ElementType.XML_PROLOG || SourceTreeToPsiMap.treeElementToPsi(myNode) instanceof XmlDocument) {
      return getFormatter().getNoneIndent();
    }
    else if (myNode.getElementType() == ElementType.XML_COMMENT && myNode.textContains('\n')) {
      return getFormatter().createAbsoluteNoneIndent();
    }
    else if (myNode.getElementType() == ElementType.XML_DOCUMENT) {
      return getFormatter().getNoneIndent();
    }
    else if (myNode.getElementType() == ElementType.XML_TAG) {
      return getFormatter().getNoneIndent();
    }    
    else {
      return null;
    }
    */
    return myIndent;
  }

  public boolean insertLineBreakBeforeTag() {
    return false;
  }

  public boolean removeLineBreakBeforeTag() {
    return false;
  }

  public boolean isTextElement() {
    return myNode.getElementType() == ElementType.XML_TEXT
           || myNode.getElementType() == ElementType.XML_DATA_CHARACTERS
           || myNode.getElementType() == ElementType.XML_CHAR_ENTITY_REF
      ;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myNode.getElementType() == ElementType.JSP_DECLARATION || myNode.getElementType() == JspElementType.JSP_SCRIPTLET) {
      return new ChildAttributes(Formatter.getInstance().createNormalIndent(), null);
    }
    else if (myNode.getPsi() instanceof PsiFile) {
      return new ChildAttributes(Formatter.getInstance().getNoneIndent(), null);
    }
    else {
      return super.getChildAttributes(newChildIndex);
    }
  }

  public XmlFormattingPolicy getPolicy() {
    return myXmlFormattingPolicy;
  }
}
