package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.ArrayList;
import java.util.List;

public class YAMLPlainTextImpl extends YAMLScalarImpl implements YAMLScalar {
  public YAMLPlainTextImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public List<TextRange> getContentRanges() {
    final int myStart = getTextOffset();
    final ASTNode node = getNode();
    final List<TextRange> result = new ArrayList<TextRange>();

    boolean seenText = false;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == YAMLTokenTypes.TEXT) {
        seenText = true;
        result.add(child.getTextRange().shiftRight(-myStart));
      }
      else if (child.getElementType() == YAMLTokenTypes.EOL) {
        if (!seenText) {
          result.add(child.getTextRange().shiftRight(-myStart));
        }
        seenText = false;
      }
    }

    return result;
  }

  @NotNull
  @Override
  protected String getRangesJoiner(@NotNull CharSequence leftString, @NotNull CharSequence rightString) {
    if (leftString.equals("\n") || rightString.equals("\n")) {
      return "";
    }
    else {
      return " ";
    }
  }

  @Override
  public String toString() {
    return "YAML plain scalar text";
  }


  @Override
  public boolean isMultiline() {
    return getNode().findChildByType(YAMLTokenTypes.EOL) != null;
  }
}
