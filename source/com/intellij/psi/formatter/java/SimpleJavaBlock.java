package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.formatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.PsiFile;
import com.intellij.codeFormatting.general.FormatterUtil;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;


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
      if (ElementType.COMMENT_BIT_SET.contains(child.getElementType()) || child.getElementType() == JavaDocElementType.DOC_COMMENT) {
        result.add(createJavaBlock(child, mySettings, Indent.getNoneIndent(), null, null));
        indent = Indent.getNoneIndent();
      }
      else if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        break;
      }
      child = child.getTreeNext();
    }

    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        child = processChild(result, child, childAlignment, childWrap, indent);
        if (indent != null && !(myNode.getPsi() instanceof PsiFile) && child.getElementType() != ElementType.MODIFIER_LIST) {
          indent = Indent.getContinuationIndent();
        }
        //indent = FormatterEx.getInstance().getContinuationIndent();
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }

    return result;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myNode.getElementType() == ElementType.CONDITIONAL_EXPRESSION && mySettings.ALIGN_MULTILINE_TERNARY_OPERATION) {
      final Alignment usedAlignment = getUsedAlignment(newChildIndex);
      if (usedAlignment != null) {
        return new ChildAttributes(null, usedAlignment);        
      } else {
        return super.getChildAttributes(newChildIndex);
      }
    } else {
      return super.getChildAttributes(newChildIndex);
    }
  }

  protected Wrap getReservedWrap() {
    return myReservedWrap;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
    myReservedWrap = reservedWrap;
  }
}
