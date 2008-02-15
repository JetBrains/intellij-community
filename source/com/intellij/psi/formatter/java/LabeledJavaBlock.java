package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import org.jetbrains.annotations.NotNull;

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
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();
    Indent currentIndent = getLabelIndent();
    Wrap currentWrap = null;
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        result.add(createJavaBlock(child, mySettings, currentIndent, currentWrap, null));
        if (child.getElementType() == ElementType.COLON) {
          currentIndent = Indent.getNoneIndent();
          currentWrap =Wrap.createWrap(Wrap.ALWAYS, true);
        }
      }
      child = child.getTreeNext();
    }
    return result;
  }

  private Indent getLabelIndent() {
    if (mySettings.getIndentOptions(StdFileTypes.JAVA).LABEL_INDENT_ABSOLUTE) {
      return Indent.getAbsoluteLabelIndent();
    } else {
      return Indent.getLabelIndent();
    }
  }

  protected Wrap getReservedWrap() {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }
}
