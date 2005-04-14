package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.newXmlFormatter.xml.AbstractBlock;

import java.util.List;

public class SynteticCodeBlock implements Block, JavaBlock{
  private final List<Block> mySubBlocks;
  private final Alignment myAlignment;
  private final Indent myIndentContent;
  private final CodeStyleSettings mySettings;
  private final Wrap myWrap;

  public SynteticCodeBlock(final List<Block> subBlocks,
                           final Alignment alignment,
                           CodeStyleSettings settings,
                           Indent indent,
                           Wrap wrap) {
    myIndentContent = indent;
    mySubBlocks = subBlocks;
    myAlignment = alignment;
    mySettings = settings;
    myWrap = wrap;
  }

  public TextRange getTextRange() {
    return new TextRange(mySubBlocks.get(0).getTextRange().getStartOffset(),
                         mySubBlocks.get(mySubBlocks.size() - 1).getTextRange().getEndOffset());
  }

  public List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  public Wrap getWrap() {
    return myWrap;
  }

  public Indent getIndent() {
    return myIndentContent;
  }

  public Alignment getAlignment() {
    return myAlignment;
  }

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    return new JavaSpacePropertyProcessor(AbstractJavaBlock.getTreeNode(child2), mySettings).getResult();
  }

  public String toString() {
    final ASTNode treeNode = ((AbstractBlock)mySubBlocks.get(0)).getTreeNode();
    final TextRange textRange = getTextRange();
    return treeNode.getPsi().getContainingFile().getText().subSequence(textRange.getStartOffset(), textRange.getEndOffset()).toString();
  }

  public Block getLastChild() {
    return mySubBlocks.get(mySubBlocks.size() - 1);
  }

  public ASTNode getFirstTreeNode() {
    return AbstractJavaBlock.getTreeNode(mySubBlocks.get(0));
  }
}
