package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.Block;
import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.*;

class FormatProcessor {
  private FormattingModel myModel;
  private BlockWrapper myCurrentBlock;
  private int myReparseFromOffset = -1;

  private final Map<Block, BlockWrapper> myInfos;

  private final CodeStyleSettings.IndentOptions myIndentOption;
  private CodeStyleSettings mySettings;

  private final Collection<AlignmentImpl> myAlignedAlignments = new HashSet<AlignmentImpl>();
  private final List<BlockWrapper> myWrapCandidates = new ArrayList<BlockWrapper>();

  public FormatProcessor(FormattingModel model,
                       Block rootBlock,
                       CodeStyleSettings settings,
                       CodeStyleSettings.IndentOptions indentOptions,
                       TextRange affectedRange) {
    myModel = model;
    myIndentOption = indentOptions;
    mySettings = settings;
    final InitialInfoBuilder builder = InitialInfoBuilder.buildBlocks(rootBlock, model, affectedRange);
    myInfos = builder.getBlockToInfoMap();
    myCurrentBlock = builder.getFirstTokenBlock();
  }

  public void format() {
    formatWithoutRealModifications();

    performModifications();
  }

  public void formatWithoutRealModifications() {
    while (myCurrentBlock != null) {
      processToken();
    }
  }

  public void performModifications() {
    int shift = 0;
    WhiteSpace prev = null;
    for (Iterator<Block> iterator = myInfos.keySet().iterator(); iterator.hasNext();) {
      Block block = iterator.next();
      final WhiteSpace whiteSpace = getBlockInfo(block).getWhiteSpace();
      final String newWhiteSpace = whiteSpace.generateWhiteSpace();
      if (prev == whiteSpace || whiteSpace.isReadOnly()) continue;
      final TextRange textRange = whiteSpace.getTextRange();
      myModel.replaceWhiteSpace(new TextRange(textRange.getStartOffset() + shift, textRange.getEndOffset() + shift), newWhiteSpace);
      shift += newWhiteSpace.length() - (textRange.getLength());
      prev = whiteSpace;
    }
  }

  private BlockWrapper getBlockInfo(final Block rootBlock) {
    return myInfos.get(rootBlock);
  }

  private void processToken() {

    final SpacePropertyImpl spaceProperty = myCurrentBlock.getSpaceProperty();
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();

    whiteSpace.arrangeLineFeeds(spaceProperty);

    try {
      if (processWrap()){
        return;
      }
    }
    finally {
      if (whiteSpace.containsLineFeeds()) {
        onCurrentLineChanged();
      }
    }

    final Block block = myCurrentBlock.getBlock();;
    if (!whiteSpace.isReadOnly()) {

      if (whiteSpace.containsLineFeeds()) {
        adjustLineIndent();
      } else {
        whiteSpace.arrangeSpaces(spaceProperty);
      }
    }

    setAlignOffset(myCurrentBlock.getAlignment(), getOffsetBefore(block));

    if (myCurrentBlock.containsLineFeeds()) {
      onCurrentLineChanged();
    }
    myCurrentBlock = myCurrentBlock.getNextBlock();
  }

  private boolean processWrap() {
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
    final TextRange textRange = myCurrentBlock.getTextRange();
    final WrapImpl wrap = myCurrentBlock.getWrap();

    boolean wrapIsPresent = whiteSpace.containsLineFeeds();

    if (wrap != null) {
      wrap.processNextEntry(textRange.getStartOffset());
    }

    if (shouldUseWrap(wrap) || wrapIsPresent) {
      if (wrap != null && wrap.getFirstEntry() >= 0) {
        myReparseFromOffset = wrap.getFirstEntry();
        wrap.markAsUsed();
        shiftToOffset(myReparseFromOffset);
        myReparseFromOffset = -1;
        return true;
      } else if (wrap != null && wrapCanBeUsedInTheFuture(wrap)) {
        wrap.markAsUsed();
      }
      whiteSpace.ensureLineFeed();
    } else {
      if (wrap != null) {
        if (isCandidateToBeWrapped(wrap)){
          myWrapCandidates.clear();
          myWrapCandidates.add(myCurrentBlock);
        }
        if (wrapCanBeUsedInTheFuture(wrap)) {
          wrap.saveFirstEntry(textRange.getStartOffset());
        }
      }
    }

    if (!whiteSpace.containsLineFeeds() && lineOver() && !myWrapCandidates.isEmpty() && !whiteSpace.isReadOnly()) {
      myCurrentBlock = myWrapCandidates.get(myWrapCandidates.size() - 1);
      return true;
    }

    return false;
  }

  private boolean isCandidateToBeWrapped(final WrapImpl wrap) {
    return isSuitableInTheCurrentPosition(wrap) && (wrap.getType() == WrapImpl.Type.WRAP_AS_NEEDED || wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED);
  }

  private void onCurrentLineChanged() {
    myAlignedAlignments.clear();
    myWrapCandidates.clear();
  }

  private void adjustLineIndent() {
    int alignOffset = getAlignOffset(myCurrentBlock.getAlignment());
    if (alignOffset == -1) {
      final Block block = myCurrentBlock.getBlock();
      Block current = getParentWithTheSameOffset(block);
      final Block candidate = getNearestIndentedParent(block.getParent(), current);
      final int indent = calculateIndentUnder(candidate);
      myCurrentBlock.getWhiteSpace().setSpaces(indent);
    } else {
      myCurrentBlock.getWhiteSpace().setSpaces(alignOffset);
    }
  }

  private Block getParentWithTheSameOffset(Block block) {
    while (block != null) {
      final Block parent = block.getParent();
      if (parent == null) return block;
      final BlockWrapper parentInfo = getBlockInfo(parent);
      if (parentInfo.getTextRange().getStartOffset() != getBlockInfo(block).getTextRange().getStartOffset()) return block;
      block = parent;
    }
    return null;
  }

  private void shiftToOffset(final int marker) {
    while (myCurrentBlock.getPreviousBlock() != null && myCurrentBlock.getTextRange().getStartOffset() > marker) {
      if (myCurrentBlock.getWhiteSpace().containsLineFeeds()) {
        onCurrentLineChanged();
      }
      myCurrentBlock = myCurrentBlock.getPreviousBlock();
      if (myCurrentBlock.containsLineFeeds()) {
        onCurrentLineChanged();
      }
    }
  }

  private Block getNearestIndentedParent(Block block, Block current) {
    if (block == null) return null;
    final Block parent = block.getParent();
    if (parent == null) return null;
    if (!current.skipIndent(parent) && getBlockInfo(block).notIsFirstElement()) return parent;
    return getNearestIndentedParent(parent, current);
  }

  private boolean wrapCanBeUsedInTheFuture(final WrapImpl wrap) {
    return wrap != null && wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED &&
           (isSuitableInTheCurrentPosition(wrap));
  }

  private boolean isSuitableInTheCurrentPosition(final WrapImpl wrap) {
    return wrap.isWrapFirstElement() || (wrap.getFirstPosition() < myCurrentBlock.getTextRange().getStartOffset());
  }

  private boolean shouldUseWrap(final WrapImpl wrap) {
    if (myWrapCandidates.contains(myCurrentBlock)) return true;
    if (wrap == null) return false;
    if (!isSuitableInTheCurrentPosition(wrap)) return false;
    if (wrap.isIsActive()) return true;
    final WrapImpl.Type type = wrap.getType();
    if (type == WrapImpl.Type.WRAP_ALWAYS) return true;
    if (type == WrapImpl.Type.WRAP_AS_NEEDED || type == WrapImpl.Type.CHOP_IF_NEEDED) {
      return lineOver();
    }
    return false;
  }

  private boolean lineOver() {
    return getOffsetBefore(myCurrentBlock) >= mySettings.RIGHT_MARGIN;
  }

  private int getOffsetBefore(final Block block) {
    int result = 0;
    BlockWrapper info = getBlockInfo(block);
    while (true) {
      final WhiteSpace whiteSpace = info.getWhiteSpace();
      result += whiteSpace.getSpaces();
      if (whiteSpace.containsLineFeeds()){
        return result;
      }
      info = info.getPreviousBlock();
      if (info == null) return result;
      result += info.getSymbolsAtTheLastLine();
      if (info.containsLineFeeds()) return result;
    }
  }

  private void setAlignOffset(final AlignmentImpl alignment, final int currentIndent) {
    if (alignment != null) {
      if (!myAlignedAlignments.contains(alignment)) {
        alignment.setCurrentOffset(currentIndent);
      }
      myAlignedAlignments.add(alignment);
    }
  }

  private int getAlignOffset(final AlignmentImpl alignment) {
    if (alignment == null) return -1;
    return alignment.getCurrentOffset();
  }

  private int calculateIndentUnder(Block block) {
    if (block == null) return 0;
    final BlockWrapper info = getBlockInfo(block);
    if (info.getWhiteSpace().containsLineFeeds()) {
      final IndentImpl indent = getBlockInfo(block).getChildIndent();
      final int offsetBeforeBlock = getOffsetBefore(info);
      return offsetBeforeBlock + getIndent(indent);
    }
    return calculateIndentUnder(block.getParent());
  }

  private int getIndent(final IndentImpl indent) {
    if (indent == null) return myIndentOption.CONTINUATION_INDENT_SIZE;
    if (indent.getType() == IndentImpl.Type.NONE) return 0;
    if (indent.getType() == IndentImpl.Type.NORMAL) return myIndentOption.INDENT_SIZE;
    return myIndentOption.LABEL_INDENT_SIZE;
  }

  private int getOffsetBefore(final BlockWrapper info) {
    final List<Block> subBlocks = info.getSubBlocks();
    if (subBlocks.isEmpty()) {
      return getOffsetBefore(info.getBlock());
    } else {
      return getOffsetBefore(getBlockInfo(subBlocks.get(0)));
    }
  }

}
