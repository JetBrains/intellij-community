package com.intellij.psi.formatter.newXmlFormatter;

import com.intellij.newCodeFormatting.Block;
import com.intellij.newCodeFormatting.Indent;
import com.intellij.newCodeFormatting.SpaceProperty;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;

import java.util.List;

public class SynteticBlock extends AbstractSynteticBlock implements Block{
  private final List<Block> mySubBlocks;

  SynteticBlock(final List<Block> subBlocks, final Block parent, final Indent indent, XmlFormattingPolicy policy) {
    super(subBlocks, parent, policy, indent);
    mySubBlocks = subBlocks;
  }

  public TextRange getTextRange() {
    return calculateTextRange(mySubBlocks);
  }

  public List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    final IElementType type1 = ((AbstractXmlBlock)child1).myNode.getElementType();
    final IElementType type2 = ((AbstractXmlBlock)child2).myNode.getElementType();

    if (isXmlTagName(type1, type2)){
      final int spaces = myXmlFormattingPolicy.getShouldAddSpaceAroundTagName() ? 1 : 0;
      return getFormatter().createSpaceProperty(spaces, spaces, 0, getMaxLine());
    } else if (type2 == ElementType.XML_ATTRIBUTE) {
      return getFormatter().createSpaceProperty(1, 1, 0, getMaxLine());
    } else if (((AbstractXmlBlock)child1).isTextElement() && ((AbstractXmlBlock)child2).isTextElement()) {
      return getFormatter().createSafeSpace();
    } else if (type2 == getTagType() && (type1 == ElementType.XML_TEXT || type1== getTagType())
               && ((AbstractXmlBlock)child2).insertLineBreakBeforeTag()) {
      //<tag/>text <tag/></tag>
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 2, getMaxLine());
    } else if (type2 == getTagType() && (type1 == ElementType.XML_TEXT || type1== getTagType())
               && ((AbstractXmlBlock)child2).removeLineBreakBeforeTag()) {
      //<tag/></tag> text</tag>
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, 0);
    }

    if (type1 == getTagType() && type2 == ElementType.XML_TEXT) {     //<tag/>-text
      return getFormatter().createSpaceProperty(0, 0, 1, getMaxLine());
    }
    return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, getMaxLine());
  }

}
