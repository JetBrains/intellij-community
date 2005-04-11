package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;

import java.util.List;
import java.util.ArrayList;

public class ExtendsListBlock extends SimpleJavaBlock{
  public ExtendsListBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    final ArrayList<Block> result = new ArrayList<Block>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();
    ASTNode child = myNode.getFirstChildNode();

    ArrayList<Block> syntBlock = new ArrayList<Block>();

    Alignment alignment = alignList() ? Formatter.getInstance().createAlignment() : null;

    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        if (ElementType.KEYWORD_BIT_SET.isInSet(child.getElementType())) {
          if (!syntBlock.isEmpty()) {
            result.add(new SynteticCodeBlock(syntBlock,
                                             alignment,
                                             mySettings,
                                             null,
                                             null));
          }
          result.add(createJavaBlock(child, mySettings, null, arrangeChildWrap(child, childWrap), alignment));
        } else {
          child = processChild(syntBlock, child, childAlignment, childWrap);
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }

    if (!syntBlock.isEmpty()) {
      result.add(new SynteticCodeBlock(syntBlock,
                                       alignment,
                                       mySettings,
                                       null,
                                       null));
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
}
