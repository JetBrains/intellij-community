package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SimpleJavaBlock extends AbstractJavaBlock {
  private int myStartOffset = -1;
  private Map<IElementType, Wrap> myReservedWrap = new HashMap<IElementType, Wrap>();

  public SimpleJavaBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent,settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();
    int offset = myStartOffset != -1 ? myStartOffset : child != null ? child.getTextRange().getStartOffset():0;
    final ArrayList<Block> result = new ArrayList<Block>();

    Indent indent = null;
    while (child != null) {
      if (StdTokenSets.COMMENT_BIT_SET.contains(child.getElementType()) || child.getElementType() == JavaDocElementType.DOC_COMMENT) {
        result.add(createJavaBlock(child, mySettings, Indent.getNoneIndent(), null, null));
        indent = Indent.getNoneIndent();
      }
      else if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        break;
      }

      offset += child.getTextLength();
      child = child.getTreeNext();
    }

    Alignment childAlignment = createChildAlignment();
    Alignment childAlignment2 = createChildAlignment2(childAlignment);
    Wrap childWrap = createChildWrap();
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        final ASTNode astNode = child;
        child = processChild(result, astNode, chooseAlignment(childAlignment, childAlignment2, child) , childWrap, indent, offset);
        if (astNode != child && child != null) {
          offset = child.getTextRange().getStartOffset();
        }
        if (indent != null && !(myNode.getPsi() instanceof PsiFile) && child.getElementType() != ElementType.MODIFIER_LIST) {
          indent = Indent.getContinuationIndent();
        }
        //indent = FormatterEx.getInstance().getContinuationIndent();
      }
      if (child != null) {
        offset += child.getTextLength();
        child = child.getTreeNext();
      }
    }

    return result;
  }

  @NotNull
  public TextRange getTextRange() {
    if (myStartOffset != -1) {
      return new TextRange(myStartOffset, myStartOffset + myNode.getTextLength());
    }
    return super.getTextRange();
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

  protected Wrap getReservedWrap(final IElementType elementType) {
    return myReservedWrap.get(elementType);
  }

  protected void setReservedWrap(final Wrap reservedWrap, final IElementType operationType) {
    myReservedWrap.put(operationType, reservedWrap);
  }

  public void setStartOffset(final int startOffset) {
    myStartOffset = startOffset;
    //if (startOffset != -1 && startOffset != myNode.getTextRange().getStartOffset()) {
    //  assert false;
    //}
  }
}
