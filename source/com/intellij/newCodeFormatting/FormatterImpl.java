package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.*;

public class FormatterImpl {
  private FormattingModel myModel;
  private BlockWrapper myCurrentBlock;
  private int myReparseFromOffset = -1;

  private final Map<Block, BlockWrapper> myInfos;

  private final CodeStyleSettings.IndentOptions myIndentOption;
  private CodeStyleSettings mySettings;

  private final Collection<Alignment> myAlignedAlignments = new HashSet<Alignment>();
  private final List<BlockWrapper> myWrapCandidates = new ArrayList<BlockWrapper>();

  public FormatterImpl(FormattingModel model, Block rootBlock, CodeStyleSettings settings, CodeStyleSettings.IndentOptions indentOptions) {
    myModel = model;
    myIndentOption = indentOptions;
    mySettings = settings;
    final InitialInfoBuilder builder = InitialInfoBuilder.buildBlocks(rootBlock, model);
    myInfos = builder.getBlockToInfoMap();
    myCurrentBlock = builder.getFirstTokenBlock();
  }

  public void format() {
    while (myCurrentBlock != null) {
      processToken();
    }

    performModifications();
  }

  private void performModifications() {
    int shift = 0;
    WhiteSpace prev = null;
    for (Iterator<Block> iterator = myInfos.keySet().iterator(); iterator.hasNext();) {
      Block block = iterator.next();
      final WhiteSpace whiteSpace = getBlockInfo(block).getWhiteSpace();
      if (prev == whiteSpace || whiteSpace.isReadOnly()) continue;
      final TextRange textRange = whiteSpace.getTextRange();
      final String newWhiteSpace = whiteSpace.generateWhiteSpace();
      myModel.replaceWhiteSpace(new TextRange(textRange.getStartOffset() + shift, textRange.getEndOffset() + shift), newWhiteSpace);
      shift += newWhiteSpace.length() - (textRange.getLength());
      prev = whiteSpace;
    }
  }

  private BlockWrapper getBlockInfo(final Block rootBlock) {
    return myInfos.get(rootBlock);
  }

  private void processToken() {

    final SpaceProperty spaceProperty = myCurrentBlock.getSpaceProperty();
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

    final Block block = myCurrentBlock.getBlock();
    if (whiteSpace.containsLineFeeds()) {
      adjustLineIndent();
    } else {
      whiteSpace.arrangeSpaces(spaceProperty);
    }

    setAlignOffset(myCurrentBlock.getAlignment(), getOffsetBefore(block));

    if (myCurrentBlock.containsLineFeeds()) {
      onCurrentLineChanged();
    }
    myCurrentBlock = myCurrentBlock.getNextBlock();
  }

  private boolean processWrap() {
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
    final TextRange textRange = myCurrentBlock.getBlock().getTextRange();
    boolean wrapIsPresent = whiteSpace.containsLineFeeds();

    final Wrap wrap = myCurrentBlock.getWrap();
    if (shouldUseWrap(wrap) || wrapIsPresent) {
      whiteSpace.ensureLineFeed();
      if (wrapCanBeUsedInTheFuture(wrap)) {
        wrap.markAsUsed();
      }
      if (wrap != null && wrap.getFirstEntry() >= 0) {
        myReparseFromOffset = wrap.getFirstEntry();
        wrap.markAsUsed();
        shiftToOffset(myReparseFromOffset);
        myReparseFromOffset = -1;
        return true;
      }
    } else if (isCandidateToBeWrapped(wrap)){
      myWrapCandidates.clear();
      myWrapCandidates.add(myCurrentBlock);
    } else if (wrapCanBeUsedInTheFuture(wrap) && !wrapIsPresent) {
      wrap.saveFirstEntry(textRange.getStartOffset());
    }

    if (!whiteSpace.containsLineFeeds() && lineOver() && !myWrapCandidates.isEmpty()) {
      myCurrentBlock = myWrapCandidates.get(myWrapCandidates.size() - 1);
      return true;
    }

    return false;
  }

  private boolean isCandidateToBeWrapped(final Wrap wrap) {
    return wrap != null && (wrap.getType() == Wrap.Type.WRAP_AS_NEEDED || wrap.getType() == Wrap.Type.CHOP_IF_NEEDED);
  }

  private void onCurrentLineChanged() {
    myAlignedAlignments.clear();
    myWrapCandidates.clear();
  }

  private void adjustLineIndent() {
    int alignOffset = getAlignOffset(myCurrentBlock.getAlignment());
    if (alignOffset == -1) {
      myCurrentBlock.getWhiteSpace().setSpaces(calculateIndentUnder(getNearestIndentedParent(myCurrentBlock.getBlock().getParent())));
    } else {
      myCurrentBlock.getWhiteSpace().setSpaces(alignOffset);
    }
  }

  private void shiftToOffset(final int marker) {
    while (myCurrentBlock.getPreviousBlock() != null && myCurrentBlock.getBlock().getTextRange().getStartOffset() > marker) {
      if (myCurrentBlock.getWhiteSpace().containsLineFeeds()) {
        onCurrentLineChanged();
      }
      myCurrentBlock = myCurrentBlock.getPreviousBlock();
      if (myCurrentBlock.containsLineFeeds()) {
        onCurrentLineChanged();
      }
    }
  }

  private Block getNearestIndentedParent(Block block) {
    if (block == null) return null;
    final Block parent = block.getParent();
    if (getBlockInfo(block).notIsFirstElement()) return parent;
    return getNearestIndentedParent(parent);
  }

  private boolean wrapCanBeUsedInTheFuture(final Wrap wrap) {
    return wrap != null && wrap.getType() == Wrap.Type.CHOP_IF_NEEDED;
  }

  private boolean shouldUseWrap(final Wrap wrap) {
    if (myWrapCandidates.contains(myCurrentBlock)) return true;
    if (wrap == null) return false;
    if (wrap.isIsActive()) return true;
    final Wrap.Type type = wrap.getType();
    if (type == Wrap.Type.WRAP_ALWAYS) return true;
    if (type == Wrap.Type.WRAP_AS_NEEDED || type == Wrap.Type.CHOP_IF_NEEDED) {
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

  private void setAlignOffset(final Alignment alignment, final int currentIndent) {
    if (alignment != null) {
      if (!myAlignedAlignments.contains(alignment)) {
        alignment.setCurrentOffset(currentIndent);
      }
      myAlignedAlignments.add(alignment);
    }
  }

  private int getAlignOffset(final Alignment alignment) {
    if (alignment == null) return -1;
    return alignment.getCurrentOffset();
  }

  private int calculateIndentUnder(Block block) {
    if (block == null) return 0;
    final BlockWrapper info = getBlockInfo(block);
    if (info.getWhiteSpace().containsLineFeeds()) {
      return getOffsetBefore(info) + getIndent(block);
    }
    return calculateIndentUnder(block.getParent());
  }

  private int getOffsetBefore(final BlockWrapper info) {
    final List<Block> subBlocks = info.getBlock().getSubBlocks();
    if (subBlocks.isEmpty()) {
      return getOffsetBefore(info.getBlock());
    } else {
      return getOffsetBefore(getBlockInfo(subBlocks.get(0)));
    }
  }

  private int getIndent(final Block block) {
    final Indent indent = getBlockInfo(block).getChildIndent();
    if (indent == null) return myIndentOption.CONTINUATION_INDENT_SIZE;
    if (indent.getType() == Indent.Type.NORMAL) return myIndentOption.INDENT_SIZE;
    return myIndentOption.LABEL_INDENT_SIZE;
  }

}
