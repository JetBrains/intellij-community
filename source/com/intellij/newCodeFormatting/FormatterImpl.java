package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.*;

public class FormatterImpl {
  private final Stack<BlockInfo> myStack = new Stack<BlockInfo>();
  private FormattingModel myModel;
  private Block myRootBlock;
  private int myCurrentLine = -1;
  private int myCurrentOffset = -1;

  private final Map <Block, WhiteSpace> myWhiteSpaceBeforeBlock = new LinkedHashMap<Block, WhiteSpace>();
  private final CodeStyleSettings.IndentOptions myIndentOption;
  private CodeStyleSettings mySettings;

  public FormatterImpl(FormattingModel model, Block rootBlock, CodeStyleSettings settings, CodeStyleSettings.IndentOptions indentOptions) {
    myModel = model;
    myRootBlock = rootBlock;
    myIndentOption = indentOptions;
    mySettings = settings;
  }

  public void format() {
    myWhiteSpaceBeforeBlock.putAll(WhiteSpacesBuilder.buildWhiteSpaces(myRootBlock, myModel));
    myCurrentLine = 0;
    myCurrentOffset  = 0;

    processBlock(myRootBlock, null);

    int shift = 0;
    WhiteSpace prev = null;
    for (Iterator<Block> iterator = myWhiteSpaceBeforeBlock.keySet().iterator(); iterator.hasNext();) {
      Block block = iterator.next();
      final WhiteSpace whiteSpace = myWhiteSpaceBeforeBlock.get(block);
      if (prev == whiteSpace) continue;
      final TextRange textRange = whiteSpace.getTextRange();
      final String newWhiteSpace = whiteSpace.generateWhiteSpace();
      myModel.replaceWhiteSpace(new TextRange(textRange.getStartOffset() + shift, textRange.getEndOffset() + shift), newWhiteSpace);
      shift += newWhiteSpace.length() - (textRange.getLength());
      prev = whiteSpace;
    }
  }

  private void processBlock(final Block rootBlock, final SpaceProperty spaceProperty) {
    final WhiteSpace whiteSpace = myWhiteSpaceBeforeBlock.get(rootBlock);
    final BlockInfo info = new BlockInfo(rootBlock);
    info.setAlignment(rootBlock.getAlignment());
    info.setWrap(rootBlock.getWrap());
    BlockInfo parent = myStack.isEmpty() ? null : myStack.peek();
    myStack.push(info);
    if (parent != null && !parent.isFirstElementProcessed()) {
      if (info.getAlignment() == null) {
        info.setAlignment(parent.getAlignment());
      }
      if (info.getWrap() == null) {
        info.setWrap(parent.getWrap());
      }

    }
    try {
      final List<Block> subBlocks = rootBlock.getSubBlocks();
      if (subBlocks.isEmpty()) {
        processToken(spaceProperty, whiteSpace);
      } else {
        processCompositeBlock(subBlocks, spaceProperty, rootBlock);
      }
    }
    finally {
      myStack.pop();
    }
  }

  private void processCompositeBlock(final List<Block> subBlocks,
                                     final SpaceProperty spaceProperty,
                                     final Block rootBlock) {
    Block previous = null;
    for (Iterator<Block> iterator = subBlocks.iterator(); iterator.hasNext();) {
      final Block current = iterator.next();
      processBlock(current, previous == null ? spaceProperty : rootBlock.getSpaceProperty(previous, current));
      previous = current;
    }
  }

  private void processToken(final SpaceProperty spaceProperty,
                            final WhiteSpace whiteSpace) {
    final BlockInfo info = myStack.peek();
    final Wrap wrap = info.getWrap();
    whiteSpace.arrangeLineFeeds(spaceProperty);
    if (shouldUseWrap(wrap)) {
      whiteSpace.ensureLineFeed();
    }
    final int wsLineFeeds = whiteSpace.getLineFeeds();
    if (wsLineFeeds > 0) {
      myCurrentLine += wsLineFeeds;
      myCurrentOffset = whiteSpace.getSpaces();
    } else {
      myCurrentOffset += whiteSpace.getSpaces();
    }
    if (!onTheSameLine(whiteSpace)) {
      final int before = whiteSpace.getSpaces();
      int alignOffset = getAlignOffset(info.getAlignment());
      if (alignOffset == -1) {
        int indent = calculateIndent();
        setFirstElementIsProcessed(indent);
        whiteSpace.setSpaces(indent);
      } else {
        setFirstElementIsProcessed(alignOffset);
        whiteSpace.setSpaces(alignOffset);
      }
      final int after = whiteSpace.getSpaces();
      myCurrentOffset += after - before;
    } else {
      setElementIsProcessed(myCurrentOffset);
      whiteSpace.arrangeSpaces(spaceProperty);
    }

    setAlignOffset(info.getAlignment(), info.getCurrentIndent(), myCurrentLine);
    final TextRange textRange = info.getBlock().getTextRange();
    final int blockLineFeeds = getLineFeeds(textRange);
    if (blockLineFeeds > 0) {
      myCurrentLine += blockLineFeeds;
      myCurrentOffset = getLastLineLength(textRange);
    } else {
      myCurrentOffset += textRange.getLength();
    }
  }

  private boolean shouldUseWrap(final Wrap wrap) {
    if (wrap == null) return false;
    final Wrap.Type type = wrap.getType();
    if (type == Wrap.Type.WRAP_ALWAYS) return true;
    if (type == Wrap.Type.WRAP_AS_NEEDED || type == Wrap.Type.CHOP_IF_NEEDED) {
      return myCurrentOffset >= mySettings.RIGHT_MARGIN;
    }
    return false;
  }

  private int getLastLineLength(final TextRange textRange) {
    return textRange.getEndOffset() - myModel.getLineStartOffset(myModel.getLineNumber(textRange.getEndOffset()));
  }

  private int getLineFeeds(final TextRange textRange) {
    return myModel.getLineNumber(textRange.getEndOffset()) - myModel.getLineNumber(textRange.getStartOffset());
  }

  private void setAlignOffset(final Alignment alignment, final int currentIndent, final int lineNumber) {
    if (alignment != null) {
      alignment.setCurrentOffset(currentIndent, lineNumber);
    }
  }

  private int getAlignOffset(final Alignment alignment) {
    if (alignment == null) return -1;
    return alignment.getCurrentOffset();
  }

  private void setElementIsProcessed(final int offset) {
    for (int i = 0; i < myStack.size(); i++) {
      final BlockInfo stackElement = myStack.get(i);
      if (!stackElement.isFirstElementProcessed()){
        stackElement.setCurrentIndent(offset);
        stackElement.setIsAtTheStartOfLine(false);
      }
      stackElement.setFirstElementProcessed(true);
    }
  }

  private void setFirstElementIsProcessed(final int indent) {
    for (int i = 0; i < myStack.size(); i++) {
      final BlockInfo stackElement = myStack.get(i);
      if (!stackElement.isFirstElementProcessed()){
        stackElement.setCurrentIndent(indent);
        stackElement.setIsAtTheStartOfLine(true);
      }
      stackElement.setFirstElementProcessed(true);
    }

  }

  private int calculateIndent() {
    int result = 0;
    for (int i = 0; i < myStack.size(); i++) {
      final BlockInfo stackElement = myStack.get(i);
      if (stackElement.getCurrentIndent() >= 0 && stackElement.isIsAtTheStartOfLine()){
        result = stackElement.getCurrentIndent() + getIndent(stackElement);
      }
    }
    return result;
  }

  private int getIndent(final BlockInfo stackElement) {
    final Indent indent = stackElement.getBlock().getChildIndent();
    if (indent == null) return myIndentOption.CONTINUATION_INDENT_SIZE;
    if (indent.getType() == Indent.Type.NORMAL) return myIndentOption.INDENT_SIZE;
    return myIndentOption.LABEL_INDENT_SIZE;
  }

  private boolean onTheSameLine(WhiteSpace space) {
    return !space.containsLineFeeds();
  }
}
