package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.IncorrectOperationException;

import java.util.*;
import java.io.File;
import java.io.IOException;

import org.jdom.Element;
import org.jdom.Document;

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
    final Element element = saveToXml(rootBlock);
    try {
      JDOMUtil.writeDocument(new Document(element), new File("c:/temp/format.xml"), "\n");
    }
    catch (IOException e) {
      //ignore
    }
  }

  public Element saveToXml(Block root){
    final Element result = new Element("Block");
    final TextRange textRange = root.getTextRange();
    result.setAttribute("start", String.valueOf(textRange.getStartOffset()));
    result.setAttribute("stop", String.valueOf(textRange.getEndOffset()));
    final Alignment alignment = root.getAlignment();
    if (alignment != null) {
      result.addContent(save((AlignmentImpl)alignment));
    }
    final Indent indent = root.getIndent();
    if (indent != null) {
      result.addContent(save((IndentImpl)indent));
    }

    final Wrap wrap = root.getWrap();
    if (wrap != null) {
      result.addContent(save((WrapImpl)wrap));
    }

    final List<Block> subBlocks = root.getSubBlocks();
    Block prev = null;
    for (Iterator<Block> iterator = subBlocks.iterator(); iterator.hasNext();) {
      Block block = iterator.next();
      if (prev != null) {
        final SpaceProperty spaceProperty = root.getSpaceProperty(prev, block);
        if (spaceProperty != null) {
          result.addContent(save((SpacePropertyImpl)spaceProperty));
        }
      }
      result.addContent(saveToXml(block));
      prev = block;
    }

    return result;
  }

  private Element save(final SpacePropertyImpl spaceProperty) {
    final Element result = new Element("Space");
    result.setAttribute("minspace", String.valueOf(spaceProperty.getMinSpaces()));
    result.setAttribute("maxspace", String.valueOf(spaceProperty.getMaxSpaces()));
    result.setAttribute("minlf", String.valueOf(spaceProperty.getMinLineFeeds()));
    result.setAttribute("maxlf", String.valueOf(spaceProperty.getMaxLineFeeds()));
    return result;
  }

  private Element save(final WrapImpl wrap) {
    final Element result = new Element("Wrap");
    result.setAttribute("type", wrap.getType().toString());
    result.setAttribute("id", wrap.getId());
    return result;
  }

  private Element save(final IndentImpl indent) {
    final Element element = new Element("Indent");
    element.setAttribute("type", indent.getType().toString());
    return element;
  }

  private Element save(final AlignmentImpl alignment) {
    final Element result = new Element("Alignment");
    result.setAttribute("type", alignment.getType().toString());
    result.setAttribute("id", alignment.getId());
    return result;
  }

  public void format() throws IncorrectOperationException {
    formatWithoutRealModifications();

    myModel.runModificationTransaction(new Runnable() {
      public void run() {
        performModifications();
      }
    });

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
    if (rootBlock == null) return null;
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
    return isSuitableInTheCurrentPosition(wrap) &&
           (wrap.getType() == WrapImpl.Type.WRAP_AS_NEEDED || wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED)
          && !myCurrentBlock.getWhiteSpace().isReadOnly();
  }

  private void onCurrentLineChanged() {
    myAlignedAlignments.clear();
    myWrapCandidates.clear();
  }

  private void adjustLineIndent() {
    int alignOffset = getAlignOffset(myCurrentBlock.getAlignment());
    if (alignOffset == -1) {
      myCurrentBlock.arrangeBlockOffset(myIndentOption);
      final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
      whiteSpace.setSpaces(myCurrentBlock.getBlockOffset());
      myCurrentBlock.fixOffset(whiteSpace.getSpaces());
    } else {
      myCurrentBlock.getWhiteSpace().setSpaces(alignOffset);
    }
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
    if (myCurrentBlock.containsLineFeeds()) return false;
    return getOffsetBefore(myCurrentBlock) + myCurrentBlock.getTextRange().getLength() > mySettings.RIGHT_MARGIN;
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

  private int getOffsetBefore(final BlockWrapper info) {
    final List<Block> subBlocks = info.getSubBlocks();
    if (subBlocks.isEmpty()) {
      return getOffsetBefore(info.getBlock());
    } else {
      return getOffsetBefore(getBlockInfo(subBlocks.get(0)));
    }
  }

}
