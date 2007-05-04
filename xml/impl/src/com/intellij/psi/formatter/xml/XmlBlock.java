package com.intellij.psi.formatter.xml;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class XmlBlock extends AbstractXmlBlock {
  private final Indent myIndent;
  private TextRange myTextRange;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.xml.XmlBlock");

  public XmlBlock(final ASTNode node,
                  final Wrap wrap,
                  final Alignment alignment,
                  final XmlFormattingPolicy policy,
                  final Indent indent,
                  final TextRange textRange) {
    super(node, wrap, alignment, policy);
    myIndent = indent;
    myTextRange = textRange;
  }

  @NotNull
  public TextRange getTextRange() {
    if (myTextRange != null) {
      return myTextRange;
    }
    else {
      return super.getTextRange();
    }
  }

  protected List<Block> buildChildren() {

    if (myNode.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE || myNode.getElementType() == XmlElementType.XML_COMMENT) {
      return EMPTY;
    }

    if (myNode.getElementType() == XmlElementType.XML_TEXT) {
      if (myXmlFormattingPolicy.getShouldKeepWhiteSpaces()) {
        return EMPTY;
      }

      final ASTNode treeParent = myNode.getTreeParent();
      final XmlTag tag = getTag(treeParent);
      if (tag != null) {
        if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(tag)) {
          return EMPTY;
        }
      }
    }

    if (myNode instanceof CompositeElement) {
      final ArrayList<Block> result = new ArrayList<Block>(5);
      ChameleonTransforming.transformChildren(myNode);
      ASTNode child = myNode.getFirstChildNode();
      while (child != null) {
        if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
          child = processChild(result, child, null, null, getChildDefaultIndent());
        }
        if (child != null) {
          LOG.assertTrue(child.getTreeParent() == myNode);
          child = child.getTreeNext();
        }
      }
      return result;
    } else {
      return EMPTY;
    }
  }

  @Nullable
  private Indent getChildDefaultIndent() {
    if (myNode.getElementType() == XmlElementType.HTML_DOCUMENT) {
      return Indent.getNoneIndent();
    }
    if (myNode.getElementType() == ElementType.DUMMY_HOLDER) {
      return Indent.getNoneIndent();
    }
    if (myNode.getElementType() == XmlElementType.XML_PROLOG) {
      return Indent.getNoneIndent();
    }
    if (myNode.getElementType() == JspElementType.JSP_SCRIPTLET) {
      return Indent.getNoneIndent();
    }
    if (myNode.getElementType() == JspElementType.JSP_DECLARATION) {
      return Indent.getNoneIndent();
    }
    if (myNode.getElementType() == JspElementType.JSP_EXPRESSION) {
      return Indent.getNoneIndent();
    }

    else {
      return null;
    }
  }

  public Spacing getSpacing(Block child1, Block child2) {
    if (!(child1 instanceof AbstractBlock) || !(child2 instanceof AbstractBlock)) {
      return null;
    }

    final IElementType elementType = myNode.getElementType();
    final ASTNode node1 = ((AbstractBlock)child1).getNode();
    final IElementType type1 = node1.getElementType();
    final ASTNode node2 = ((AbstractBlock)child2).getNode();
    final IElementType type2 = node2.getElementType();

    if ((isXmlTag(node2) || type2 == XmlElementType.XML_END_TAG_START || type2 == XmlElementType.XML_TEXT) && myXmlFormattingPolicy
      .getShouldKeepWhiteSpaces()) {
      return Spacing.getReadOnlySpacing();
    }

    if (type1 == JspElementType.JSP_DECLARATION_START || type1 == JspElementType.JSP_SCRIPTLET_START) {
      return Spacing.createDependentLFSpacing(0, 1, node2.getTextRange(), myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                              myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (type2 == JspElementType.JSP_DECLARATION_END || type2 == JspElementType.JSP_SCRIPTLET_END) {
      return Spacing.createDependentLFSpacing(0, 1, node1.getTextRange(), myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                              myXmlFormattingPolicy.getKeepBlankLines());

    }

    if (elementType == XmlElementType.XML_TEXT) {
      return getSpacesInsideText(type1, type2);

    }
    else if (elementType == XmlElementType.XML_ATTRIBUTE) {
      return getSpacesInsideAttribute(type1, type2);
    }

    if (type1 == XmlElementType.XML_PROLOG) {
      return createDefaultSpace(true, false);
    }

    if (elementType == XmlElementType.XML_DOCTYPE) {
      return createDefaultSpace(true, false);
    }

    return createDefaultSpace(false, false);
  }

  private Spacing getSpacesInsideAttribute(final IElementType type1, final IElementType type2) {
    if (type1 == XmlElementType.XML_EQ || type2 == XmlElementType.XML_EQ) {
      int spaces = myXmlFormattingPolicy.getShouldAddSpaceAroundEqualityInAttribute() ? 1 : 0;
      return Spacing
        .createSpacing(spaces, spaces, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }
    else {
      return createDefaultSpace(false, false);
    }
  }

  private Spacing getSpacesInsideText(final IElementType type1, final IElementType type2) {
    if (type1 == XmlElementType.XML_DATA_CHARACTERS && type2 == XmlElementType.XML_DATA_CHARACTERS) {
      return Spacing
        .createSpacing(1, 1, 0, myXmlFormattingPolicy.getShouldKeepLineBreaksInText(), myXmlFormattingPolicy.getKeepBlankLines());
    }
    else {
      return createDefaultSpace(false, true);
    }
  }

  public Indent getIndent() {
    if (myNode.getElementType() == XmlElementType.XML_PROLOG || myNode.getElementType() == XmlElementType.XML_DOCTYPE ||
        SourceTreeToPsiMap.treeElementToPsi(myNode) instanceof XmlDocument) {
      return Indent.getNoneIndent();
    }
    /*
    else if (myNode.getElementType() == ElementType.XML_COMMENT && myNode.textContains('\n')) {
      return getFormatter().getAbsoluteNoneIndent();
    }
    */

    /*
    if (myNode.getElementType() == ElementType.JSP_SCRIPTLET || myNode.getElementType() == ElementType.JSP_DECLARATION) {
      return getFormatter().getNoneIndent();
    }
    if (myNode.getElementType() == ElementType.JSP_XML_TEXT) {
      return getFormatter().getNormalIndent();
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
      return getFormatter().getAbsoluteNoneIndent();
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
    return myNode.getElementType() == XmlElementType.XML_TEXT || myNode.getElementType() == XmlElementType.XML_DATA_CHARACTERS ||
           myNode.getElementType() == XmlElementType.XML_CHAR_ENTITY_REF;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myNode.getElementType() == JspElementType.JSP_DECLARATION || myNode.getElementType() == JspElementType.JSP_SCRIPTLET) {
      final List<Block> subBlocks = getSubBlocks();
      if (subBlocks.size() == 3 && subBlocks.get(1) instanceof JspTextBlock) {
        final JspTextBlock jspTextBlock = ((JspTextBlock)subBlocks.get(1));
        if (newChildIndex == 2 && jspTextBlock.isTailIncomplete()) {
          return ChildAttributes.DELEGATE_TO_PREV_CHILD;
        }
        if (newChildIndex == 1 && jspTextBlock.isHeadIncomplete()) {
          return ChildAttributes.DELEGATE_TO_NEXT_CHILD;
        }
      }

      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    else if (myNode.getPsi() instanceof PsiFile) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
    else {
      return super.getChildAttributes(newChildIndex);
    }
  }

  public XmlFormattingPolicy getPolicy() {
    return myXmlFormattingPolicy;
  }
}
