package com.intellij.psi.formatter.newXmlFormatter.xml;

import com.intellij.newCodeFormatting.Block;
import com.intellij.newCodeFormatting.Indent;
import com.intellij.newCodeFormatting.SpaceProperty;
import com.intellij.newCodeFormatting.ChildAttributes;
import com.intellij.openapi.util.TextRange;

import java.util.List;
import java.util.ArrayList;

public class ReadOnlySyntheticBlock extends AbstractSyntheticBlock{
  private final TextRange myTextRange;

  private static final ArrayList<Block> EMPTY = new ArrayList<Block>();

  public ReadOnlySyntheticBlock(List<Block> subBlocks, Block parent, XmlFormattingPolicy policy, Indent indent) {
    super(subBlocks, parent, policy, indent);

    myTextRange = calculateTextRange(subBlocks);

  }

  public TextRange getTextRange() {
    return myTextRange;
  }

  public List<Block> getSubBlocks() {
    return EMPTY;
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    return null;
  }

  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getIndent(), null);
  }
}
