package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.formatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.codeFormatting.general.FormatterUtil;

import java.util.List;
import java.util.ArrayList;

public class ExtendsListBlock extends AbstractJavaBlock{
  public ExtendsListBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, CodeStyleSettings settings) {
    super(node, wrap, alignment, Indent.getNoneIndent(), settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    final ArrayList<Block> result = new ArrayList<Block>();
    ArrayList<Block> elementsExceptKeyword = new ArrayList<Block>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();
    ASTNode child = myNode.getFirstChildNode();

    Alignment alignment = alignList() ? Alignment.createAlignment() : null;

    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        if (ElementType.KEYWORD_BIT_SET.isInSet(child.getElementType())) {
          if (!elementsExceptKeyword.isEmpty()) {
            result.add(new SynteticCodeBlock(elementsExceptKeyword, null,  mySettings, Indent.getNoneIndent(), null));
            elementsExceptKeyword = new ArrayList<Block>();
          }
          result.add(createJavaBlock(child, mySettings, Indent.createContinuationIndent(), arrangeChildWrap(child, childWrap), alignment));
        } else {
          processChild(elementsExceptKeyword, child, childAlignment, childWrap, Indent.createContinuationIndent());
          
        }
      }
      child = child.getTreeNext();
    }
    if (!elementsExceptKeyword.isEmpty()) {
      result.add(new SynteticCodeBlock(elementsExceptKeyword, alignment,  mySettings, Indent.getNoneIndent(), null));
    }
    
    return result;

  }

  private boolean alignList() {
    if (myNode.getElementType() == ElementType.EXTENDS_LIST) {
      return mySettings.ALIGN_MULTILINE_EXTENDS_LIST;
    } else if (myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      return mySettings.ALIGN_MULTILINE_EXTENDS_LIST;
    } else if (myNode.getElementType() == ElementType.THROWS_LIST) {
      return mySettings.ALIGN_MULTILINE_THROWS_LIST;
    }
    return false;
  }

  protected Wrap getReservedWrap() {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
  }
  
}
