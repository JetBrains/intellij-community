package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InitialInfoBuilder {

  private static final Logger LOG = Logger.getInstance("#com.intellij.newCodeFormatting.InitialInfoBuilder");

  private WhiteSpace myCurrentWhiteSpace;
  private final FormattingModel myModel;
  private final Map<Block, BlockWrapper> myResult = new LinkedHashMap<Block, BlockWrapper>();
  private BlockWrapper myPreviousBlock;
  private BlockWrapper myFirstTokenBlock;

  private InitialInfoBuilder(final FormattingModel model) {
    myModel = model;
    myCurrentWhiteSpace = new WhiteSpace(0, 0, 0, 0, true);
  }

  public static final InitialInfoBuilder buildBlocks(Block root, FormattingModel model) {
    final InitialInfoBuilder builder = new InitialInfoBuilder(model);
    builder.buildFrom(root);
    return builder;
  }

  private void buildFrom(final Block rootBlock) {
    final TextRange textRange = rootBlock.getTextRange();
    final int blockStartOffset = textRange.getStartOffset();
    myCurrentWhiteSpace.append(blockStartOffset, myModel);
    final BlockWrapper info = new BlockWrapper(rootBlock, myCurrentWhiteSpace, myModel, myPreviousBlock);
    myResult.put(rootBlock, info);
    final List<Block> subBlocks = rootBlock.getSubBlocks();
    if (subBlocks.isEmpty()) {
      LOG.assertTrue(rootBlock.getTextRange().getLength() > 0);
      if (myPreviousBlock != null) {
        myPreviousBlock.setNextBlock(info);
      }
      if (myFirstTokenBlock == null) {
        myFirstTokenBlock = info;
      }
      myCurrentWhiteSpace = new WhiteSpace(textRange.getEndOffset(), textRange.getEndOffset(), 0, 0, false);
      myPreviousBlock = info;
    } else {
      for (Iterator<Block> iterator = subBlocks.iterator(); iterator.hasNext();) {
        buildFrom(iterator.next());
      }
    }
  }

  public Map<Block, BlockWrapper> getBlockToInfoMap() {
    return myResult;
  }

  public BlockWrapper getFirstTokenBlock() {
    return myFirstTokenBlock;
  }
}
