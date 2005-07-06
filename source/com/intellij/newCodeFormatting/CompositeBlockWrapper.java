package com.intellij.newCodeFormatting;

import com.intellij.newCodeFormatting.Block;
import com.intellij.newCodeFormatting.AbstractBlockWrapper;
import com.intellij.openapi.util.TextRange;

public class CompositeBlockWrapper extends AbstractBlockWrapper{
  public CompositeBlockWrapper(final Block block, final WhiteSpace whiteSpace, final AbstractBlockWrapper parent, TextRange textRange) {
    super(block, whiteSpace, parent, textRange);
  }
}
