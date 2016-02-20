package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class YAMLBlockScalarImpl extends YAMLScalarImpl {
  protected static final int DEFAULT_CONTENT_INDENT = 2;
  
  public YAMLBlockScalarImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  protected abstract IElementType getContentType();

  @Override
  public boolean isMultiline() {
    return true;
  }

  @NotNull
  @Override
  public List<TextRange> getContentRanges() {
    final int myStart = getTextOffset();
    final ASTNode node = getNode();
    final List<TextRange> result = new ArrayList<TextRange>();

    final int indent = locateIndent();

    final ASTNode firstEol = node.findChildByType(YAMLTokenTypes.EOL);
    if (firstEol == null) {
      return Collections.emptyList();
    }

    int thisLineStart = firstEol.getStartOffset() + 1;
    for (ASTNode child = firstEol.getTreeNext(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == getContentType()) {
        assert thisLineStart != -1;
        result.add(TextRange.create(thisLineStart, child.getTextRange().getEndOffset()).shiftRight(-myStart));
        thisLineStart = -1;

        if (node.findChildByType(getContentType(), child.getTreeNext()) == null) {
          break;
        }
      }
      else if (child.getElementType() == YAMLTokenTypes.INDENT) {
        thisLineStart = child.getStartOffset() + Math.min(indent, child.getTextLength());
      }
      if (child.getElementType() == YAMLTokenTypes.EOL) {
        if (thisLineStart != -1) {
          result.add(TextRange.create(thisLineStart, child.getStartOffset()).shiftRight(-myStart));
        }
        thisLineStart = child.getStartOffset() + 1;
      }
    }

    return result;
  }

  protected int locateIndent() {
    int number = 0;
    for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == getContentType()) {
        number++;
        if (number == 2) {
          return YAMLUtil.getIndentInThisLine(child.getPsi());
        }
      }
    }
    return 0;
  }
}
