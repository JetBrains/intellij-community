package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhiteSpacesBuilder {
  private WhiteSpace myCurrentWhiteSpace;
  private final FormattingModel myModel;
  private final Map<Block, WhiteSpace> myResult = new LinkedHashMap<Block, WhiteSpace>();

  private WhiteSpacesBuilder(final FormattingModel model) {
    myModel = model;
    myCurrentWhiteSpace = new WhiteSpace(0, 0, 0, 0, true);
  }

  public static final Map<Block, WhiteSpace> buildWhiteSpaces(Block root, FormattingModel model) {
    final WhiteSpacesBuilder builder = new WhiteSpacesBuilder(model);
    builder.buildFrom(root);
    return builder.myResult;
  }

  private void buildFrom(final Block rootBlock) {
    final TextRange textRange = rootBlock.getTextRange();
    final int blockStartOffset = textRange.getStartOffset();
    myCurrentWhiteSpace.append(blockStartOffset, myModel);
    myResult.put(rootBlock, myCurrentWhiteSpace);
    final List<Block> subBlocks = rootBlock.getSubBlocks();
    if (subBlocks.isEmpty()) {
      myCurrentWhiteSpace = new WhiteSpace(textRange.getEndOffset(), textRange.getEndOffset(), 0, 0, false);
    } else {
      for (Iterator<Block> iterator = subBlocks.iterator(); iterator.hasNext();) {
        buildFrom(iterator.next());
      }
    }
  }


}
