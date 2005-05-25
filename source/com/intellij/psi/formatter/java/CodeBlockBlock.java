package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;

import java.util.ArrayList;
import java.util.List;

public class CodeBlockBlock extends AbstractJavaBlock {
  private final static int BEFORE_FIRST = 0;
  private final static int BEFORE_LBRACE = 1;
  private final static int INSIDE_BODY = 2;
  private final static int AFTER_CASE_LABEL = 3;
  
  private final int myChildrenIndent;

  public CodeBlockBlock(final ASTNode node,
                        final Wrap wrap,
                        final Alignment alignment,
                        final Indent indent,
                        final CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
    if (isSwitchCodeBlock() && !settings.INDENT_CASE_FROM_SWITCH) {
      myChildrenIndent = 0;
    }else {
      myChildrenIndent = 1;
    }
  }

  private boolean isSwitchCodeBlock() {
    return myNode.getTreeParent().getElementType() == ElementType.SWITCH_STATEMENT;
  }

  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<Block>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();

    buildChildren(result, childAlignment, childWrap);

    return result;

  }

  private void buildChildren(final ArrayList<Block> result, final Alignment childAlignment, final Wrap childWrap) {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();

    int state = BEFORE_FIRST;
    
    if (myNode.getPsi() instanceof JspClass) {
      state = INSIDE_BODY;
    }
    
    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        final Indent indent = calcCurrentIndent(child, state);
        state = calcNewState(child, state);
        child = processChild(result, child, childAlignment, childWrap, indent);
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }
  }

  private int calcNewState(final ASTNode child, int state) {
    switch (state) {
      case BEFORE_FIRST:
      {
        if (ElementType.COMMENT_BIT_SET.isInSet(child.getElementType())) {
          return BEFORE_FIRST;
        }
        else if (child.getElementType() == ElementType.LBRACE) {
          return INSIDE_BODY;
        }
        else {
          return BEFORE_LBRACE;
        }
      }
      case BEFORE_LBRACE:
      {
        if (child.getElementType() == ElementType.LBRACE) {
          return INSIDE_BODY;
        }
        else {
          return BEFORE_LBRACE;
        }
      }
      case INSIDE_BODY:
      {
        if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT) {
          return AFTER_CASE_LABEL;
        }
        break;
      }
      case AFTER_CASE_LABEL:
        return AFTER_CASE_LABEL;
    }
    return INSIDE_BODY;
  }

  private Indent calcCurrentIndent(final ASTNode child, final int state) {
    if (child.getElementType() == ElementType.RBRACE) {
      return Formatter.getInstance().getNoneIndent();
    }
    
    if (state == BEFORE_FIRST) return Formatter.getInstance().getNoneIndent();
    
    if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT) {
      return getCodeBlockInternalIndent(child, myChildrenIndent);
    }
    if (state == AFTER_CASE_LABEL) {
      if (child.getElementType() == ElementType.BLOCK_STATEMENT) {
        return getCodeBlockInternalIndent(child, myChildrenIndent);
      } else {
        return getCodeBlockInternalIndent(child, myChildrenIndent + 1);
      }
    }
    if (state == BEFORE_LBRACE) {
      if (child.getElementType() == ElementType.LBRACE) {
        return Formatter.getInstance().getNoneIndent();
      }
      else {
        return Formatter.getInstance().createContinuationIndent();
      }
    }
    else {
      if (child.getElementType() == ElementType.RBRACE) {
        return Formatter.getInstance().getNoneIndent();
      }
      else {
        return getCodeBlockInternalIndent(child, 1);
      }
    }
  }

  protected Wrap getReservedWrap() {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
  }
}
