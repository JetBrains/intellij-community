// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.ArrayList;
import java.util.List;

public class YAMLPlainTextImpl extends YAMLBlockScalarImpl implements YAMLScalar {
  public YAMLPlainTextImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected @NotNull IElementType getContentType() {
    return YAMLTokenTypes.TEXT;
  }

  @Override
  protected boolean getIncludeFirstLineInContent() {
    return true;
  }

  @Override
  public @NotNull YamlScalarTextEvaluator getTextEvaluator() {
    return new YamlScalarTextEvaluator<>(this) {

      @Override
      public @NotNull List<TextRange> getContentRanges() {
        final int myStart = getTextRange().getStartOffset();
        final List<TextRange> result = new ArrayList<>();

        boolean seenText = false;
        for (ASTNode child = getFirstContentNode(); child != null; child = child.getTreeNext()) {
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

      @Override
      protected @NotNull String getRangesJoiner(@NotNull CharSequence text, @NotNull List<TextRange> contentRanges, int indexBefore) {
        if (isNewline(text, contentRanges.get(indexBefore)) || isNewline(text, contentRanges.get(indexBefore + 1))) {
          return "";
        }
        else {
          return " ";
        }
      }

      private static boolean isNewline(@NotNull CharSequence text, @NotNull TextRange range) {
        return range.getLength() == 1 && text.charAt(range.getStartOffset()) == '\n';
      }
    };
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
