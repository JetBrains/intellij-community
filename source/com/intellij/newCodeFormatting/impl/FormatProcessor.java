package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import gnu.trove.TIntObjectHashMap;
import org.jdom.Element;
import org.jdom.Text;

import java.util.*;

class FormatProcessor {
  private LeafBlockWrapper myCurrentBlock;

  private final Map<Block, AbstractBlockWrapper> myInfos;
  private final TIntObjectHashMap<LeafBlockWrapper> myTextRangeToWrapper;

  private final CodeStyleSettings.IndentOptions myIndentOption;
  private CodeStyleSettings mySettings;

  private final Collection<AlignmentImpl> myAlignedAlignments = new HashSet<AlignmentImpl>();
  LeafBlockWrapper myWrapCandidate = null;
  private final LeafBlockWrapper myFirstTokenBlock;

  private Map<TextRange, Pair<AbstractBlockWrapper, Boolean>> myPreviousDependancies = new HashMap<TextRange, Pair<AbstractBlockWrapper, Boolean>>();
  private boolean myAlignAgain = false;
  private final WhiteSpace myLastWhiteSpace;

  public FormatProcessor(final FormattingDocumentModel docModel, Block rootBlock,
                         CodeStyleSettings settings,
                         CodeStyleSettings.IndentOptions indentOptions,
                         TextRange affectedRange) {
    myIndentOption = indentOptions;
    mySettings = settings;
    final InitialInfoBuilder builder = InitialInfoBuilder.buildBlocks(rootBlock,
                                                                      docModel,
                                                                      affectedRange,
                                                                      indentOptions);
    myInfos = builder.getBlockToInfoMap();
    myFirstTokenBlock = builder.getFirstTokenBlock();
    myCurrentBlock = myFirstTokenBlock;
    myTextRangeToWrapper = buildTextRangeToInfoMap(myFirstTokenBlock);
    myLastWhiteSpace = new WhiteSpace(getLastBlock().getTextRange().getEndOffset(), false);
    myLastWhiteSpace.append(docModel.getTextLength(), docModel, indentOptions);
  }

  private LeafBlockWrapper getLastBlock() {
    LeafBlockWrapper result = myFirstTokenBlock;
    while (result.getNextBlock() != null) {
      result =  result.getNextBlock();
    }
    return result;
  }

  private TIntObjectHashMap<LeafBlockWrapper> buildTextRangeToInfoMap(final LeafBlockWrapper first) {
    final TIntObjectHashMap<LeafBlockWrapper> result = new TIntObjectHashMap<LeafBlockWrapper>();
    LeafBlockWrapper current = first;
    while (current != null) {
      result.put(current.getTextRange().getStartOffset(), current);
      current = current.getNextBlock();
    }
    return result;
  }

  public Element saveToXml(Block root) {
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
    result.addContent(new Text(root.toString()));
    return result;
  }

  private Element save(final SpacePropertyImpl spaceProperty) {
    final Element result = new Element("Space");
    spaceProperty.refresh(this);
    result.setAttribute("minspace", String.valueOf(spaceProperty.getMinSpaces()));
    result.setAttribute("maxspace", String.valueOf(spaceProperty.getMaxSpaces()));
    result.setAttribute("minlf", String.valueOf(spaceProperty.getMinLineFeeds()));
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
    result.setAttribute("id", alignment.getId());
    return result;
  }

  public void format(FormattingModel model){
    formatWithoutRealModifications();
    performModifications(model);
  }

  public void formatWithoutRealModifications() {
    while (true) {
      myAlignAgain = false;
      myCurrentBlock = myFirstTokenBlock;
      while (myCurrentBlock != null) {
        processToken();
      }
      if (!myAlignAgain) return;
      reset();
    }
  }

  private void reset() {
    myAlignedAlignments.clear();
    myPreviousDependancies.clear();
    myWrapCandidate = null;
    for (Iterator<AbstractBlockWrapper> iterator = myInfos.values().iterator(); iterator.hasNext();) {
      AbstractBlockWrapper blockWrapper = iterator.next();
      blockWrapper.reset();
    }
  }

  public void performModifications(FormattingModel model){
    int shift = 0;
    WhiteSpace prev = null;
    for (LeafBlockWrapper block = myFirstTokenBlock; block != null; block = block.getNextBlock()) {
      final WhiteSpace whiteSpace = block.getWhiteSpace();
      if (!whiteSpace.isReadOnly()) {
        final int oldTextRangeLength = block.getTextRange().getLength();
        final String newWhiteSpace = whiteSpace.generateWhiteSpace(myIndentOption);
        if (prev == whiteSpace || whiteSpace.isReadOnly()) continue;
        if (whiteSpace.equals(newWhiteSpace)) continue;
        final TextRange textRange = whiteSpace.getTextRange();
        final TextRange wsRange = shiftRange(textRange, shift);
        final int newBlockLength = model.replaceWhiteSpace(wsRange, 
                                                             newWhiteSpace, 
                                                             block.getTextRange().getLength());
        shift += (newWhiteSpace.length() - (textRange.getLength())) + (newBlockLength - oldTextRangeLength);
      }
      prev = whiteSpace;
    }
  }

  private TextRange shiftRange(final TextRange textRange, final int shift) {
    return new TextRange(textRange.getStartOffset() + shift, textRange.getEndOffset() + shift);
  }

  private AbstractBlockWrapper getBlockInfo(final Block rootBlock) {
    if (rootBlock == null) return null;
    return myInfos.get(rootBlock);
  }

  private void processToken() {

    final SpacePropertyImpl spaceProperty = myCurrentBlock.getSpaceProperty();
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();

    whiteSpace.arrangeLineFeeds(spaceProperty, this);

    if (!whiteSpace.containsLineFeeds()) {
      whiteSpace.arrangeSpaces(spaceProperty);
    }

    try {
      if (processWrap(spaceProperty)) {
        return;
      }
    }
    finally {
      if (whiteSpace.containsLineFeeds()) {
        onCurrentLineChanged();
      }
    }

    if (whiteSpace.containsLineFeeds()) {
      adjustLineIndent();
    }
    else {
      whiteSpace.arrangeSpaces(spaceProperty);
    }

    setAlignOffset(myCurrentBlock);

    if (myCurrentBlock.containsLineFeeds()) {
      onCurrentLineChanged();
    }

    if (shouldSaveDependancy(spaceProperty, whiteSpace)) {
      saveDependancy(spaceProperty);
    }

    if (!myAlignAgain) {
      myAlignAgain = shouldReformatBecauseOfBackwardDependance(whiteSpace.getTextRange());
    }

    myCurrentBlock = myCurrentBlock.getNextBlock();
  }

  private boolean shouldReformatBecauseOfBackwardDependance(TextRange changed) {
    for (Iterator<TextRange> iterator = myPreviousDependancies.keySet().iterator(); iterator.hasNext();) {
      TextRange textRange = iterator.next();
      final Pair<AbstractBlockWrapper, Boolean> pair = myPreviousDependancies.get(textRange);
      final boolean containedLineFeeds = pair.getSecond().booleanValue();
      if (textRange.getStartOffset() <= changed.getStartOffset() && textRange.getEndOffset() >= changed.getEndOffset()) {
        boolean containsLineFeeds = containsLineFeeds(textRange);
        if (containedLineFeeds != containsLineFeeds) {
          return true;
        }
      }
    }
    return false;
  }

  private void saveDependancy(final SpacePropertyImpl spaceProperty) {
    final TextRange dependancy = ((DependantSpacePropertyImpl)spaceProperty).getDependancy();
    myPreviousDependancies.put(dependancy,
                               new Pair<AbstractBlockWrapper, Boolean>(myCurrentBlock, new Boolean(containsLineFeeds(dependancy))));
  }

  private boolean shouldSaveDependancy(final SpacePropertyImpl spaceProperty, WhiteSpace whiteSpace) {
    if (!(spaceProperty instanceof DependantSpacePropertyImpl)) return false;

    final TextRange dependancy = ((DependantSpacePropertyImpl)spaceProperty).getDependancy();
    if (whiteSpace.getTextRange().getStartOffset() >= dependancy.getEndOffset()) return false;
    return true;
  }

  private boolean processWrap(SpaceProperty spaceProperty) {
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
    final TextRange textRange = myCurrentBlock.getTextRange();
    final WrapImpl[] wraps = myCurrentBlock.getWraps();

    if (whiteSpace.containsLineFeeds() && !whiteSpace.containsLineFeedsInitially()) {
      whiteSpace.removeLineFeeds(spaceProperty, this);
    }

    boolean wrapIsPresent = whiteSpace.containsLineFeeds();

    for (int i = 0; i < wraps.length; i++) {
      WrapImpl wrap = wraps[i];
      wrap.processNextEntry(textRange.getStartOffset());
    }

    WrapImpl wrap = getWrapToBeUsed(wraps);

    if (wrap != null || wrapIsPresent) {
      if (!wrapIsPresent && !canReplaceWrapCandidate(wrap)) {
        myCurrentBlock = myWrapCandidate;
        return true;
      }
      else {
        if (wrap != null && wrap.getFirstEntry() != null) {
          myCurrentBlock = wrap.getFirstEntry();
          wrap.markAsUsed();
          return true;
        }
        else {
          if (wrap != null && wrapCanBeUsedInTheFuture(wrap)) {
            wrap.markAsUsed();
          }
        }
      }

      if (!whiteSpace.containsLineFeeds()) {
        whiteSpace.ensureLineFeed();
//        releaseAlignments();
      }

      myWrapCandidate = null;
    }
    else {
      for (int i = 0; i < wraps.length; i++) {
        WrapImpl wrap1 = wraps[i];
        if (isCandidateToBeWrapped(wrap1) && canReplaceWrapCandidate(wrap1)) {
          myWrapCandidate = myCurrentBlock;
        }
        if (wrapCanBeUsedInTheFuture(wrap1)) {
          wrap1.saveFirstEntry(myCurrentBlock);
        }

      }
    }

    if (!whiteSpace.containsLineFeeds() && lineOver() && myWrapCandidate != null && !whiteSpace.isReadOnly()) {
      myCurrentBlock = myWrapCandidate;
      return true;
    }

    return false;
  }

  private boolean canReplaceWrapCandidate(WrapImpl wrap) {
    if (myWrapCandidate == null) return true;
    final WrapImpl currentWrap = myWrapCandidate.getWrap();
    if (wrap == currentWrap) return true;
    return !wrap.isChildOf(currentWrap);
  }

  private boolean isCandidateToBeWrapped(final WrapImpl wrap) {
    return isSuitableInTheCurrentPosition(wrap) &&
           (wrap.getType() == WrapImpl.Type.WRAP_AS_NEEDED || wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED) &&
           !myCurrentBlock.getWhiteSpace().isReadOnly();
  }

  private void onCurrentLineChanged() {
    /*
    for (Iterator<AlignmentImpl> iterator = myAlignedAlignments.iterator(); iterator.hasNext();) {
      AlignmentImpl alignment = iterator.next();
      alignment.setOffsetRespBlock(-1);
    }
    */
    myAlignedAlignments.clear();
    myWrapCandidate = null;
  }

  private void adjustLineIndent() {
    int alignOffset = getAlignOffset();
    if (alignOffset == -1) {
      final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
      final IndentData offset = myCurrentBlock.calculateOffset(myIndentOption);
      whiteSpace.setSpaces(offset.getSpaces(), offset.getIndentSpaces());
    }
    else {
      final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
      AbstractBlockWrapper previousIndentedBlock = getPreviousIndentedBlock();
      if (previousIndentedBlock == null) {
        whiteSpace.setSpaces(alignOffset, 0);
      }
      else {
        int indentOffset = previousIndentedBlock.getWhiteSpace().getIndentOffset();
        if (indentOffset > alignOffset) {
          whiteSpace.setSpaces(alignOffset, 0);
        }
        else {
          whiteSpace.setSpaces(alignOffset - indentOffset, indentOffset);
        }

      }
    }
  }

  private AbstractBlockWrapper getPreviousIndentedBlock() {
    AbstractBlockWrapper current = myCurrentBlock.getParent();
    while (current != null) {
      if (current.getStartOffset() != myCurrentBlock.getStartOffset() && current.getWhiteSpace().containsLineFeeds()) return current;
      current = current.getParent();
    }
    return null;
  }

  private boolean wrapCanBeUsedInTheFuture(final WrapImpl wrap) {
    return wrap != null && wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED && (isSuitableInTheCurrentPosition(wrap));
  }

  private boolean isSuitableInTheCurrentPosition(final WrapImpl wrap) {
    if (wrap.getFirstPosition() < myCurrentBlock.getTextRange().getStartOffset()) {
      return true;
    }

    if (wrap.isWrapFirstElement()) {
      return true;
    }

    if (wrap.getType() == WrapImpl.Type.WRAP_AS_NEEDED) {
      return positionAfterWrappingIsSutable();
    }

    if (wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED) {
      return lineOver() && positionAfterWrappingIsSutable();
    }

    return false;
  }

  private boolean positionAfterWrappingIsSutable() {
    boolean result = true;
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
    if (whiteSpace.containsLineFeeds()) return true;
    final int spaces = whiteSpace.getSpaces();
    int indentSpaces = whiteSpace.getIndentSpaces();
    try {
      final int offsetBefore = getOffsetBefore(myCurrentBlock.getBlock());
      whiteSpace.ensureLineFeed();
      adjustLineIndent();
      final int offsetAfter = getOffsetBefore(myCurrentBlock.getBlock());
      if (offsetBefore <= offsetAfter) {
        result = false;
      }
    }
    finally {
      whiteSpace.removeLineFeeds(myCurrentBlock.getSpaceProperty(), this);
      whiteSpace.setSpaces(spaces, indentSpaces);
    }
    return result;
  }

  private WrapImpl getWrapToBeUsed(final WrapImpl[] wraps) {
    if (wraps.length == 0) return null;
    if (myWrapCandidate == myCurrentBlock) return wraps[0];
    for (int i = 0; i < wraps.length; i++) {
      WrapImpl wrap = wraps[i];
      if (!isSuitableInTheCurrentPosition(wrap)) continue;
      if (wrap.isIsActive()) return wrap;
      final WrapImpl.Type type = wrap.getType();
      if (type == WrapImpl.Type.WRAP_ALWAYS) return wrap;
      if (type == WrapImpl.Type.WRAP_AS_NEEDED || type == WrapImpl.Type.CHOP_IF_NEEDED) {
        if (lineOver()) {
          return wrap;
        }
      }

    }
    return null;
  }

  private boolean lineOver() {
    if (myCurrentBlock.containsLineFeeds()) return false;
    return getOffsetBefore(myCurrentBlock.getBlock()) + myCurrentBlock.getTextRange().getLength() > mySettings.RIGHT_MARGIN;
  }

  private int getOffsetBefore(final Block block) {
    int result = 0;
    LeafBlockWrapper info = (LeafBlockWrapper)getBlockInfo(block);
    while (true) {
      final WhiteSpace whiteSpace = info.getWhiteSpace();
      result += whiteSpace.getTotalSpaces();
      if (whiteSpace.containsLineFeeds()) {
        return result;
      }
      info = info.getPreviousBlock();
      if (info == null) return result;
      result += info.getSymbolsAtTheLastLine();
      if (info.containsLineFeeds()) return result;
    }
  }

  private void setAlignOffset(final LeafBlockWrapper block) {
    AbstractBlockWrapper current = myCurrentBlock;
    while (true) {
      final AlignmentImpl alignment = (AlignmentImpl)current.getBlock().getAlignment();
      if (alignment != null && !myAlignedAlignments.contains(alignment)) {
        alignment.setOffsetRespBlock(block);
        myAlignedAlignments.add(alignment);
      }
      current = current.getParent();
      if (current == null) return;
      if (current.getStartOffset() != myCurrentBlock.getStartOffset()) return;

    }
  }

  private int getAlignOffset() {
    AbstractBlockWrapper current = myCurrentBlock;
    while (true) {
      final AlignmentImpl alignment = (AlignmentImpl)current.getBlock().getAlignment();
      if (alignment != null && alignment.getOffsetRespBlockBefore(myCurrentBlock) != null) {
        return getOffsetBefore(alignment.getOffsetRespBlockBefore(myCurrentBlock).getBlock());
      }
      else {
        current = current.getParent();
        if (current == null) return -1;
        if (current.getStartOffset() != myCurrentBlock.getStartOffset()) return -1;
      }
    }
  }

  public boolean containsLineFeeds(final TextRange dependance) {
    LeafBlockWrapper child = myTextRangeToWrapper.get(dependance.getStartOffset());
    if (child.containsLineFeeds()) return true;
    final int endOffset = dependance.getEndOffset();
    while (child.getTextRange().getEndOffset() < endOffset) {
      child = child.getNextBlock();
      if (child.getWhiteSpace().containsLineFeeds()) return true;
      if (child.containsLineFeeds()) return true;
    }
    return false;
  }

  public LeafBlockWrapper getBlockBefore(final int startOffset) {
    int current = startOffset;
    while (current < myLastWhiteSpace.getTextRange().getStartOffset()) {
      final LeafBlockWrapper currentValue = myTextRangeToWrapper.get(current);
      if (currentValue != null) return currentValue;
      current++;
    }
    return null;
  }

  public void setAllWhiteSpacesAreReadOnly() {
    LeafBlockWrapper current = myFirstTokenBlock;
    while (current != null) {
      current.getWhiteSpace().setReadOnly(true);
      current = current.getNextBlock();
    }
  }

  public IndentInfo getIndentAt(final int offset) {
    processBlocksBefore(offset);
    AbstractBlockWrapper parent = getParentFor(offset, myCurrentBlock);
    if (parent == null) return new IndentInfo(0, 0, 0);
    final int index = getNewChildPosition(parent, offset);
    ChildAttributes childAttributes = parent.getBlock().getChildAttributes(index);
    final IndentInfo result = adjustLineIndent(parent, childAttributes, index);
    processToken();
    return result;
  }

  private IndentInfo adjustLineIndent(final AbstractBlockWrapper parent, final ChildAttributes childAttributes, final int index) {
    int alignOffset = getAlignOffsetBefore(childAttributes.getAlignment(), null);
    if (alignOffset == -1) {
      return parent.calculateChildOffset(myIndentOption, childAttributes, index).createIndentInfo();
    }
    else {
      AbstractBlockWrapper previousIndentedBlock = getPreviousIndentedBlock();
      if (previousIndentedBlock == null) {
        return new IndentInfo(0, 0, alignOffset);
      }
      else {
        int indentOffset = previousIndentedBlock.getWhiteSpace().getIndentOffset();
        if (indentOffset > alignOffset) {
          return new IndentInfo(0, 0, alignOffset);
        }
        else {
          return new IndentInfo(0, indentOffset, alignOffset - indentOffset);
        }
      }
    }
  }

  private int getAlignOffsetBefore(final Alignment alignment, final LeafBlockWrapper blockAfter) {
    if (alignment == null) return -1;
    return getOffsetBefore(((AlignmentImpl)alignment).getOffsetRespBlockBefore(blockAfter).getBlock());
  }

  private int getNewChildPosition(final AbstractBlockWrapper parent, final int offset) {
    final List<Block> subBlocks = parent.getBlock().getSubBlocks();
    for (int i = 0; i < subBlocks.size(); i++) {
      Block block = subBlocks.get(i);
      if (block.getTextRange().getStartOffset() >= offset) return i;
    }
    return subBlocks.size();
  }

  private AbstractBlockWrapper getParentFor(final int offset, AbstractBlockWrapper block) {
    AbstractBlockWrapper current = block;
    while (current != null) {
      final TextRange textRange = current.getTextRange();
      if (textRange.getStartOffset() < offset && textRange.getEndOffset() > offset) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  private AbstractBlockWrapper getParentFor(final int offset, LeafBlockWrapper block) {
    Block previous = getPreviousIncompletedBlock(block, offset);
    if (previous != null) {
      return myInfos.get(previous);
    }
    else {
      return getParentFor(offset, ((AbstractBlockWrapper)block));
    }
  }

  private Block getPreviousIncompletedBlock(final LeafBlockWrapper block, final int offset) {
    AbstractBlockWrapper current = block;
    while (current.getParent() != null && current.getParent().getStartOffset() > offset) {
      current = current.getParent();
    }
    if (current.getParent() == null) return null;

    final List<Block> subBlocks = current.getParent().getBlock().getSubBlocks();
    final int index = subBlocks.indexOf(current.getBlock());
    if (index == 0) return null;

    Block currentResult = subBlocks.get(index - 1);
    if (!currentResult.isIncomplete()) return null;

    Block lastChild = getLastChildOf(currentResult);
    while (lastChild != null && lastChild.isIncomplete()) {
      currentResult = lastChild;
      lastChild = getLastChildOf(currentResult);
    }
    return currentResult;
  }

  private Block getLastChildOf(final Block currentResult) {
    final List<Block> subBlocks = currentResult.getSubBlocks();
    if (subBlocks.isEmpty()) return null;
    return subBlocks.get(subBlocks.size() - 1);
  }

  private void processBlocksBefore(final int offset) {
    while (true) {
      myAlignAgain = false;
      myCurrentBlock = myFirstTokenBlock;
      while (myCurrentBlock != null && myCurrentBlock.getStartOffset() < offset) {
        processToken();
      }
      if (!myAlignAgain) return;
      reset();
    }
  }

  public LeafBlockWrapper getFirstTokenBlock() {
    return myFirstTokenBlock;
  }

  public WhiteSpace getLastWhiteSpace() {
    return myLastWhiteSpace;
  }
}
