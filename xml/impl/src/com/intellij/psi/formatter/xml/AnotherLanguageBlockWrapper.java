// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.xml;

import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.InjectedLanguageBlockWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AnotherLanguageBlockWrapper extends AbstractXmlBlock {
  private final InjectedLanguageBlockWrapper myInjectedBlock;
  private final Indent myIndent;

  public AnotherLanguageBlockWrapper(final ASTNode node,
                                     final XmlFormattingPolicy policy,
                                     final Block original, final Indent indent,
                                     final int offset,
                                     @Nullable TextRange range) {
    super(node, original.getWrap(), original.getAlignment(), policy, false);
    myInjectedBlock = new InjectedLanguageBlockWrapper(original, offset, range, null);
    myIndent = indent;
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  public boolean insertLineBreakBeforeTag() {
    return false;
  }

  @Override
  public boolean removeLineBreakBeforeTag() {
    return false;
  }

  @Override
  public boolean isTextElement() {
    return true;
  }

  @Override
  protected List<Block> buildChildren() {
    return myInjectedBlock.getSubBlocks();
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myInjectedBlock.getTextRange();
  }

  @Override
  public @Nullable Spacing getSpacing(Block child1, @NotNull Block child2) {
    return myInjectedBlock.getSpacing(child1,  child2);
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(final int newChildIndex) {
    return myInjectedBlock.getChildAttributes(newChildIndex);
  }
}
