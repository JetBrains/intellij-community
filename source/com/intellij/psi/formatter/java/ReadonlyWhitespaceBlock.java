/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class ReadonlyWhitespaceBlock implements Block {
  private TextRange myRange;
  private final Wrap myWrap;
  private final Alignment myAlignment;
  private final Indent myIndent;

  public ReadonlyWhitespaceBlock(final TextRange range, final Wrap wrap, final Alignment alignment, final Indent indent) {
    myRange = range;
    myWrap = wrap;
    myAlignment = alignment;
    myIndent = indent;
  }

  @NotNull
  public TextRange getTextRange() {
    return myRange;
  }

  @NotNull
  public List<Block> getSubBlocks() {
    return Collections.emptyList();
  }

  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Nullable
  public Indent getIndent() {
    return myIndent;
  }

  @Nullable
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Nullable
  public Spacing getSpacing(Block child1, Block child2) {
    return null;
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return ChildAttributes.DELEGATE_TO_NEXT_CHILD;
  }

  public boolean isIncomplete() {
    return false;
  }

  public boolean isLeaf() {
    return true;
  }
}
