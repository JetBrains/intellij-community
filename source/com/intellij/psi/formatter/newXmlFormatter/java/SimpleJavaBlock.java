package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;

import java.util.ArrayList;
import java.util.List;


public class SimpleJavaBlock extends AbstractJavaBlock {
  private Wrap myReservedWrap;

  public SimpleJavaBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent,settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode firstChild = myNode.getFirstChildNode();
    final ArrayList<Block> comments = new ArrayList<Block>();
    firstChild = readCommentsFrom(firstChild, comments);
    final ArrayList<Block> blocks = createBlocksFromChild(firstChild);
    if (comments.isEmpty()) {
      return blocks;
    } else {
      final ArrayList<Block> result = new ArrayList<Block>();
      result.addAll(comments);
      if (!blocks.isEmpty()) {
        final SynteticCodeBlock resultWrapper = new SynteticCodeBlock(blocks, myAlignment, mySettings, myIndent, myWrap);        
        result.add(resultWrapper);
      }
      myAlignment = null;
      myWrap = null;
      myIndent = Formatter.getInstance().getNoneIndent();
      return result;
    }
  }

  private ASTNode readCommentsFrom(ASTNode child, final ArrayList<Block> comments) {
    while (child != null) {
      if (ElementType.COMMENT_BIT_SET.isInSet(child.getElementType()) || child.getElementType() == JavaDocElementType.DOC_COMMENT) {
        comments.add(createJavaBlock(child, mySettings));
      }
      else if (!containsWhiteSpacesOnly(child)) {
        return child;
      }
      child = child.getTreeNext();
    }
    return child;
  }

  private ArrayList<Block> createBlocksFromChild(ASTNode child) {
    if (myNode.getElementType() == ElementType.METHOD_CALL_EXPRESSION) {
      if (getReservedWrap() == null) setReservedWrap(Formatter.getInstance().createWrap(getWrapType(mySettings.METHOD_CALL_CHAIN_WRAP), false));
    }

    final ArrayList<Block> result = new ArrayList<Block>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        child = processChild(result, child, childAlignment, childWrap);
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }

    return result;
  }

  protected Wrap getReservedWrap() {
    return myReservedWrap;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
    myReservedWrap = reservedWrap;
  }
}
