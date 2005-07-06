package com.intellij.formatting;

import com.intellij.formatting.Block;
import com.intellij.formatting.AbstractBlockWrapper;
import com.intellij.openapi.util.TextRange;

public class CompositeBlockWrapper extends AbstractBlockWrapper{
  public CompositeBlockWrapper(final Block block, final WhiteSpace whiteSpace, final AbstractBlockWrapper parent, TextRange textRange) {
    super(block, whiteSpace, parent, textRange);
  }
}
