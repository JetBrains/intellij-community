// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.formatter;


import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.restructuredtext.RestElementTypes;
import com.intellij.restructuredtext.RestTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RestBlock implements ASTBlock {
  private final ASTNode myNode;
  private final Alignment myAlignment;
  private final Indent myIndent;
  private final Wrap myWrap;
  private List<RestBlock> mySubBlocks = null;

  public RestBlock(ASTNode node, final Alignment alignment, Indent indent, Wrap wrap) {
    myNode = node;
    myAlignment = alignment;
    myIndent = indent;
    myWrap = wrap;
  }


  @Override
  public ASTNode getNode() {
    return myNode;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = buildSubBlocks();
    }
    return new ArrayList<>(mySubBlocks);
  }
  private List<RestBlock> buildSubBlocks() {
    List<RestBlock> blocks = new ArrayList<>();
    for (ASTNode child = myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {

      IElementType childType = child.getElementType();
      if (child.getTextRange().getLength() == 0) continue;
      if (childType == RestTokenTypes.WHITESPACE) {
        continue;
      }

      blocks.add(buildSubBlock(child));
    }
    return Collections.unmodifiableList(blocks);
  }

  private RestBlock buildSubBlock(ASTNode child) {
    IElementType childType = child.getElementType();
    IElementType grandparentType = myNode.getTreeParent() == null ? null : myNode.getTreeParent().getElementType();
    Indent childIndent = Indent.getNoneIndent();

    if (grandparentType == RestElementTypes.DIRECTIVE_BLOCK && childType == RestTokenTypes.FIELD) {
      childIndent = Indent.getNormalIndent();
    }
    return new RestBlock(child, null, childIndent, null);
  }


  @Override
  public @Nullable Wrap getWrap() {
    return myWrap;
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
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }
}
