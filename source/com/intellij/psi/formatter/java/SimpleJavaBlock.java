package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;


public class SimpleJavaBlock extends AbstractJavaBlock {
  private Wrap myReservedWrap;

  public SimpleJavaBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent,settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();
    final ArrayList<Block> result = new ArrayList<Block>();

    Indent indent = null;
    while (child != null) {
      if (ElementType.COMMENT_BIT_SET.isInSet(child.getElementType()) || child.getElementType() == JavaDocElementType.DOC_COMMENT) {
        result.add(createJavaBlock(child, mySettings, Formatter.getInstance().getNoneIndent(), null, null));
        indent = Formatter.getInstance().getNoneIndent();
      }
      else if (!containsWhiteSpacesOnly(child)) {
        break;
      }
      child = child.getTreeNext();
    }
    
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        child = processChild(result, child, childAlignment, childWrap, indent);
        if (indent != null && !(myNode.getPsi() instanceof PsiFile)) {
          indent = Formatter.getInstance().createContinuationIndent();
        }
        //indent = Formatter.getInstance().createContinuationIndent();
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
