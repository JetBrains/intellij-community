// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl;

import java.util.Collections;
import java.util.List;

/**
 * This class is a special case for block scalar items. We need to preserve additional spaces:
 * <pre>{@code
 *   key: |
 *         First line
 *          Second line
 * }</pre>
 * Could be shifted to
 * <pre>{@code
 *   key: |
 *     First line
 *      Second line
 * }</pre>
 *
 * And if there is explicit indent number then we need to preserve all spaces and could not shift block scalar items related to key
 *
 * See <a href="http://yaml.org/spec/1.2/spec.html#id2793652">8.1. Block Scalar Styles</a>
 */
final class YAMLBlockScalarItemBlock implements Block {
  final @NotNull TextRange myRange;
  final @Nullable Indent myIndent;
  final @Nullable Alignment myAlignment;

  private YAMLBlockScalarItemBlock(@NotNull TextRange range, @Nullable Indent indent, @Nullable Alignment alignment) {
    myRange = range;
    myIndent = indent;
    myAlignment = alignment;
  }

  @Override
  public String toString() {
    return "YAMLBlockScalarItemBlock(" + getTextRange() + ")";
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myRange;
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Wrap getWrap() {
    return null;
  }

  @Override
  public @Nullable Indent getIndent() {
    return myIndent;
  }

  @Override
  public @Nullable Alignment getAlignment() {
    return myAlignment;
  }

  @Override
  public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return null;
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(null, null);
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  static @NotNull Block createBlockScalarItem(@NotNull YAMLFormattingContext context, @NotNull ASTNode node) {
    ASTNode blockScalarNode = node.getTreeParent();
    YAMLBlockScalarImpl blockScalarImpl = (YAMLBlockScalarImpl)blockScalarNode.getPsi();

    // possible performance problem: parent full indent for every block scalar line
    int parentFullIndent = getParentFullIndent(context, blockScalarNode.getTreeParent());

    Indent indent;
    TextRange range;
    Alignment alignment = null;
    int oldOffset = Math.max(getNodeFullIndent(node) - parentFullIndent, 0);
    if (blockScalarImpl.hasExplicitIndent()) {
      range = new TextRange(node.getStartOffset() - oldOffset, node.getTextRange().getEndOffset());
      indent = Indent.getSpaceIndent(0, true);
    }
    else {
      // possible performance problem: calculating first line offset for every block scalar line
      int needOffset = Math.max(oldOffset - getFirstLineOffset(context, blockScalarImpl), 0);
      range = new TextRange(node.getStartOffset() - needOffset, node.getTextRange().getEndOffset());
      alignment = context.computeAlignment(node);
      indent = Indent.getNormalIndent(true);
    }
    return new YAMLBlockScalarItemBlock(range, indent, alignment);
  }

  private static int getFirstLineOffset(@NotNull YAMLFormattingContext context, @NotNull YAMLBlockScalarImpl blockScalarPsi) {
    int parentFullIndent = getParentFullIndent(context, blockScalarPsi.getNode().getTreeParent());
    ASTNode firstLine = blockScalarPsi.getNthContentTypeChild(1);
    if (firstLine == null) {
      return 0;
    }

    return Math.max(getNodeFullIndent(firstLine) - parentFullIndent, 0);
  }

  private static int getParentFullIndent(@NotNull YAMLFormattingContext context, @NotNull ASTNode node) {
    String fullText = context.getFullText();
    int start = node.getTextRange().getStartOffset();

    for (int cur = start - 1; cur >= 0; cur--) {
      if (fullText.charAt(cur) == '\n') {
        return start - cur - 1;
      }
      if (start - cur > 1000) {
        // So big indent has no practical use...
        return 0;
      }
    }
    return start;
  }

  private static int getNodeFullIndent(@NotNull ASTNode node) {
    ASTNode indentNode = node.getTreePrev();
    if (indentNode == null || indentNode.getElementType() != YAMLTokenTypes.INDENT) {
      return 0;
    }
    return indentNode.getTextLength();
  }
}
