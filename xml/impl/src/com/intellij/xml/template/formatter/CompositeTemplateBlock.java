// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.template.formatter;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class CompositeTemplateBlock implements Block {
  private final List<Block> mySubBlocks;
  private final TextRange myTextRange;

  public CompositeTemplateBlock(List<Block> subBlocks) {
    mySubBlocks = subBlocks;
    myTextRange = new TextRange(mySubBlocks.get(0).getTextRange().getStartOffset(),
                                mySubBlocks.get(mySubBlocks.size() - 1).getTextRange().getEndOffset());
  }

  public CompositeTemplateBlock(@NotNull TextRange range) {
    mySubBlocks = Collections.emptyList();
    myTextRange = range;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myTextRange;
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  @Override
  public Wrap getWrap() {
    return null;
  }

  @Override
  public Indent getIndent() {
    return Indent.getNoneIndent();
  }

  @Override
  public Alignment getAlignment() {
    return null;
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
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
    return false;
  }
}
