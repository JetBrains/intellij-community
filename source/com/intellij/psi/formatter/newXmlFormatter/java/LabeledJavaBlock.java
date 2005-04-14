package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;

import java.util.ArrayList;
import java.util.List;

public class LabeledJavaBlock extends AbstractJavaBlock{
  public LabeledJavaBlock(final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          final Indent indent,
                          final CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }

  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<Block>();
    List<Block> codeBlockContent = new ArrayList<Block>();
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        if (child.getElementType() == ElementType.COLON) {
          codeBlockContent.add(createJavaBlock(child, mySettings));
          result.add(new SynteticCodeBlock(codeBlockContent, null, mySettings, getLabelIndent(), null));
          codeBlockContent = new ArrayList<Block>();
        } else {
          codeBlockContent.add(createJavaBlock(child, mySettings));
        }
      }
      child = child.getTreeNext();
    }
    if (!codeBlockContent.isEmpty()) {
      result.add(new SynteticCodeBlock(codeBlockContent, null, mySettings, null,
                                       Formatter.getInstance().createWrap(getWrapType(mySettings.LABELED_STATEMENT_WRAP), true)));
    }
    return result;

  }

  public Indent getIndent() {
    return Formatter.getInstance().getNoneIndent();
  }

  private Indent getLabelIndent() {
    if (mySettings.JAVA_INDENT_OPTIONS.LABEL_INDENT_ABSOLUTE) {
      return Formatter.getInstance().createAbsoluteLabelIndent();
    } else {
      return Formatter.getInstance().createLabelIndent();
    }
  }

  protected Wrap getReservedWrap() {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
  }
  
}
