// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
class YAMLBlockScalarItemBlock implements Block {
  @NotNull
  final TextRange myRange;
  @Nullable
  final Indent myIndent;
  @Nullable
  final Alignment myAlignment;

  private YAMLBlockScalarItemBlock(@NotNull TextRange range, @Nullable Indent indent, @Nullable Alignment alignment) {
    myRange = range;
    myIndent = indent;
    myAlignment = alignment;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myRange;
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public Wrap getWrap() {
    return null;
  }

  @Nullable
  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Nullable
  @Override
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return null;
  }

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
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

  /** @return null iff it is not block scalar item */
  @NotNull
  static Block createBlockScalarItem(@NotNull YAMLFormattingContext context, @NotNull ASTNode node) {
    ASTNode blockScalarNode = node.getTreeParent();
    YAMLBlockScalarImpl blockScalarImpl = (YAMLBlockScalarImpl)blockScalarNode.getPsi();

    // possible performance problem: parent full indent for every block scalar line
    int parentFullIndent = getParentFullIndent(blockScalarNode.getTreeParent());

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
      int needOffset = Math.max(oldOffset - getFirstLineOffset(blockScalarImpl), 0);
      range = new TextRange(node.getStartOffset() - needOffset, node.getTextRange().getEndOffset());
      alignment = context.computeAlignment(node);
      indent = Indent.getNormalIndent(true);
    }
    return new YAMLBlockScalarItemBlock(range, indent, alignment);
  }

  private static int getFirstLineOffset(@NotNull YAMLBlockScalarImpl blockScalarPsi) {
    int parentFullIndent = getParentFullIndent(blockScalarPsi.getNode().getTreeParent());
    ASTNode firstLine = blockScalarPsi.getNthContentTypeChild(1);
    if (firstLine == null) {
      return 0;
    }

    return Math.max(getNodeFullIndent(firstLine) - parentFullIndent, 0);
  }

  private static int getParentFullIndent(@NotNull ASTNode node) {
    String fullText = node.getPsi().getContainingFile().getText();
    int start = node.getTextRange().getStartOffset();

    for (int cur = start - 1; cur >= 0; cur--) {
      if (fullText.charAt(cur) == '\n') {
        return start - cur - 1;
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
