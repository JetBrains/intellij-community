package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;

import java.util.ArrayList;
import java.util.List;


public class SimpleJavaBlock extends AbstractJavaBlock {
  private Wrap myReservedWrap;

  public SimpleJavaBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent,settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    return createBlocksFromChild(myNode.getFirstChildNode());
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
