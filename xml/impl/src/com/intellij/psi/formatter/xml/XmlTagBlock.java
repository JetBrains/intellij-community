package com.intellij.psi.formatter.xml;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
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
    super(node, wrap, alignment, policy);
    myIndent = indent;
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();
    final Wrap attrWrap = Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getAttributesWrap()), false);
    final Wrap textWrap = Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getTextWrap(getTag())), true);
    final Wrap tagBeginWrap = createTagBeginWrapping(getTag());
    final Alignment attrAlignment = Alignment.createAlignment();
    final Alignment textAlignment = Alignment.createAlignment();
    final ArrayList<Block> result = new ArrayList<Block>(3);
    ArrayList<Block> localResult = new ArrayList<Block>(1);

    boolean insideTag = true;

    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){

        Wrap wrap = chooseWrap(child, tagBeginWrap, attrWrap, textWrap);
        Alignment alignment = chooseAlignment(child, attrAlignment, textAlignment);

        if (child.getElementType() == XmlElementType.XML_TAG_END) {
          child = processChild(localResult,child, wrap, alignment, null);
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<Block>(1);
          insideTag = true;
        }
        else if (child.getElementType() == XmlElementType.XML_START_TAG_START) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
          }
          localResult = new ArrayList<Block>(1);
          child = processChild(localResult,child, wrap, alignment, null);
        }
        else if (child.getElementType() == XmlElementType.XML_END_TAG_START) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
            localResult = new ArrayList<Block>(1);
          }
          child = processChild(localResult,child, wrap, alignment, null);
        } else if (child.getElementType() == XmlElementType.XML_EMPTY_ELEMENT_END) {
          child = processChild(localResult,child, wrap, alignment, null);
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<Block>(1);
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

          if (localResult.size() == 1 && localResult.get(0) instanceof JspTextBlock) {
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

  protected
  @Nullable
  ASTNode processChild(List<Block> result, final ASTNode child, final Wrap wrap, final Alignment alignment, final Indent indent) {
    if (child.getElementType() == XmlElementType.XML_TEXT) {
      return createXmlTextBlocks(result, child, wrap, alignment);
    } else {
      return super.processChild(result, child, wrap, alignment, indent);
    }
  }

  private Indent getChildrenIndent() {
    return myXmlFormattingPolicy.indentChildrenOf(getTag())
           ? Indent.getNormalIndent()
           : Indent.getNoneIndent();
  }

  public Indent getIndent() {
    return myIndent;
  }

  private ASTNode createXmlTextBlocks(final List<Block> list, final ASTNode textNode, final Wrap wrap, final Alignment alignment) {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = textNode.getFirstChildNode();
    return createXmlTextBlocks(list, textNode, child, wrap, alignment);
  }

  private ASTNode createXmlTextBlocks(final List<Block> list, final ASTNode textNode, ASTNode child,
                                      final Wrap wrap,
                                      final Alignment alignment
  ) {
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
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
    return AbstractSyntheticBlock.createSynteticBlock(
      localResult, this, Indent.getNoneIndent(),
      myXmlFormattingPolicy,
     getChildrenIndent());
  }

  private Block createTagDescriptionNode(final ArrayList<Block> localResult) {
    return AbstractSyntheticBlock.createSynteticBlock(
      localResult, this, Indent.getNoneIndent(),
      myXmlFormattingPolicy,
      null);
  }

  public Spacing getSpacing(Block child1, Block child2) {
    final AbstractSyntheticBlock syntheticBlock1 = ((AbstractSyntheticBlock)child1);
    final AbstractSyntheticBlock syntheticBlock2 = ((AbstractSyntheticBlock)child2);

    if (syntheticBlock2.startsWithCDATA() || syntheticBlock1.endsWithCDATA()) {
      return Spacing.getReadOnlySpacing();
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
      return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock1.insertLineFeedAfter()) {
      return Spacing.createSpacing(0,0,1,true,myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock1.endsWithTag() && syntheticBlock2.isTagDescription()) {
      return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    } else {
      return createDefaultSpace(true, true);
    }

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

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myXmlFormattingPolicy.indentChildrenOf(getTag())) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    } else {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
  }
}
