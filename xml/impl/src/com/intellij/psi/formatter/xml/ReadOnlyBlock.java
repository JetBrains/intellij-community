package com.intellij.psi.formatter.xml;

import com.intellij.lang.ASTNode;
import com.intellij.formatting.Block;
import com.intellij.formatting.Spacing;
import com.intellij.psi.formatter.common.AbstractBlock;

import java.util.ArrayList;
import java.util.List;

public class ReadOnlyBlock extends AbstractBlock {
  private static final ArrayList<Block> EMPTY = new ArrayList<Block>();

  public ReadOnlyBlock(ASTNode node) {
    super(node, null, null);
  }

  public Spacing getSpacing(Block child1, Block child2) {
    return null;
  }

  public boolean isLeaf() {
    return true;
  }

  protected List<Block> buildChildren() {
    return EMPTY;
  }
}
