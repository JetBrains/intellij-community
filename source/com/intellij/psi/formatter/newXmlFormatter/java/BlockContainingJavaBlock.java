package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;

import java.util.ArrayList;
import java.util.List;

public class BlockContainingJavaBlock extends AbstractJavaBlock{
  public BlockContainingJavaBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    final ArrayList<Block> result = new ArrayList<Block>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();
    ASTNode child = myNode.getFirstChildNode();

    ArrayList<Block> syntBlock = new ArrayList<Block>();

    while (child != null) {
      while (child != null && !isPartOfCodeBlock(child)) {
        if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
          child = processChild(syntBlock, child, childAlignment, childWrap);
        }
        if (child != null) {
          child = child.getTreeNext();
        }
      }
      if (!syntBlock.isEmpty())  {
        result.add(new SynteticCodeBlock(syntBlock, null, mySettings, null, null));
        syntBlock = new ArrayList<Block>();
      }

      while (child != null && isPartOfCodeBlock(child)) {
        if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
          child = processChild(result, child, childAlignment, childWrap);
        }
        if (child != null) {
          child = child.getTreeNext();
        }
      }
    }

    return result;

  }

  private boolean isPartOfCodeBlock(final ASTNode child) {
    if (child == null) return false;
    if (child.getElementType() == ElementType.BLOCK_STATEMENT) return true;
    if (child.getElementType() == ElementType.CODE_BLOCK) return true;
    if (isStatement(child)) return true;
    if (containsWhiteSpacesOnly(child)) return isPartOfCodeBlock(child.getTreeNext());
    if (child.getElementType() == ElementType.END_OF_LINE_COMMENT) return isPartOfCodeBlock(child.getTreeNext());
    if (child.getElementType() == JavaDocElementType.DOC_COMMENT) return true;
    return false;
  }

  protected Wrap getReservedWrap() {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
  }
}
