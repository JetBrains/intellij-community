package com.intellij.psi.formatter.newXmlFormatter.xml;

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
    final IElementType type1 = ((AbstractBlock)child1).myNode.getElementType();
    final IElementType type2 = ((AbstractBlock)child2).myNode.getElementType();

    if (isXmlTagName(type1, type2)){
      final int spaces = myXmlFormattingPolicy.getShouldAddSpaceAroundTagName() ? 1 : 0;
      return getFormatter().createSpaceProperty(spaces, spaces, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    } else if (type2 == ElementType.XML_ATTRIBUTE) {
      return getFormatter().createSpaceProperty(1, 1, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    } else if (((AbstractXmlBlock)child1).isTextElement() && ((AbstractXmlBlock)child2).isTextElement()) {
      return getFormatter().createSafeSpace(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    } else if (type2 == getTagType() && (type1 == ElementType.XML_DATA_CHARACTERS || type1== getTagType())
               && ((AbstractXmlBlock)child2).insertLineBreakBeforeTag()) {
      //<tag/>text <tag/></tag>
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 2, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                                myXmlFormattingPolicy.getKeepBlankLines());
    }
    else if (type2 == getTagType() && (type1 == ElementType.XML_DATA_CHARACTERS || type1== getTagType())
               && ((AbstractXmlBlock)child2).removeLineBreakBeforeTag()) {
      //<tag/></tag> text</tag>
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                                myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (type1 == getTagType() && type2 == ElementType.XML_DATA_CHARACTERS) {     //<tag/>-text
      if (((AbstractXmlBlock)child1).isTextElement()) {
        return getFormatter().createSafeSpace(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
      } else {
        return getFormatter().createSpaceProperty(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
      }
    }

    if (type2 == getTagType() && type1 == ElementType.XML_DATA_CHARACTERS) {     //text-<tag/>
      if (((AbstractXmlBlock)child2).isTextElement()) {
        return getFormatter().createSafeSpace(true, myXmlFormattingPolicy.getKeepBlankLines());
      } else {
        return getFormatter().createSpaceProperty(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
      }
    }
    if (type2 == getTagType() && type1== getTagType()) {//<tag/><tag/>
      return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, true,
                                                myXmlFormattingPolicy.getKeepBlankLines());
    }

    return getFormatter().createSpaceProperty(0, Integer.MAX_VALUE, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
  }

}
