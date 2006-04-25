package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import gnu.trove.TIntObjectHashMap;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class FormatProcessor {

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormatProcessor");

  private LeafBlockWrapper myCurrentBlock;

  private final Map<Block, AbstractBlockWrapper> myInfos;
  private final TIntObjectHashMap<LeafBlockWrapper> myTextRangeToWrapper;

  private final CodeStyleSettings.IndentOptions myIndentOption;
  private CodeStyleSettings mySettings;

  private final Collection<AlignmentImpl> myAlignedAlignments = new HashSet<AlignmentImpl>();

  LeafBlockWrapper myWrapCandidate = null;
  LeafBlockWrapper myFirstWrappedBlockOnLine = null;

  private final LeafBlockWrapper myFirstTokenBlock;
  private final LeafBlockWrapper myLastTokenBlock;

  private Map<TextRange, Pair<AbstractBlockWrapper, Boolean>> myPreviousDependancies =
    new HashMap<TextRange, Pair<AbstractBlockWrapper, Boolean>>();
  private Collection<WhiteSpace> myAlignAgain = new HashSet<WhiteSpace>();
  private final WhiteSpace myLastWhiteSpace;

  public FormatProcessor(final FormattingDocumentModel docModel,
                         Block rootBlock,
                         CodeStyleSettings settings,
                         CodeStyleSettings.IndentOptions indentOptions,
                         TextRange affectedRange,
                         final boolean processHeadingWhitespace) {
    myIndentOption = indentOptions;
    mySettings = settings;
    final InitialInfoBuilder builder = InitialInfoBuilder.buildBlocks(rootBlock,
                                                                      docModel,
                                                                      affectedRange,
                                                                      indentOptions,
                                                                      processHeadingWhitespace);
    myInfos = builder.getBlockToInfoMap();
    myFirstTokenBlock = builder.getFirstTokenBlock();
    myLastTokenBlock = builder.getLastTokenBlock();
    myCurrentBlock = myFirstTokenBlock;
    myTextRangeToWrapper = buildTextRangeToInfoMap(myFirstTokenBlock);
    myLastWhiteSpace = new WhiteSpace(getLastBlock().getTextRange().getEndOffset(), false);
    myLastWhiteSpace.append(docModel.getTextLength(), docModel, indentOptions);
  }

  private LeafBlockWrapper getLastBlock() {
    LeafBlockWrapper result = myFirstTokenBlock;
    while (result.getNextBlock() != null) {
      result = result.getNextBlock();
    }
    return result;
  }

  private static TIntObjectHashMap<LeafBlockWrapper> buildTextRangeToInfoMap(final LeafBlockWrapper first) {
    final TIntObjectHashMap<LeafBlockWrapper> result = new TIntObjectHashMap<LeafBlockWrapper>();
    LeafBlockWrapper current = first;
    while (current != null) {
      result.put(current.getTextRange().getStartOffset(), current);
      current = current.getNextBlock();
    }
    return result;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
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
    for (Block block : subBlocks) {
      if (prev != null) {
        final Spacing spacing = root.getSpacing(prev, block);
        if (spacing != null) {
          result.addContent(save((SpacingImpl)spacing));
        }
      }
      result.addContent(saveToXml(block));

      prev = block;
    }
    result.addContent(new Text(root.toString()));
    return result;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private Element save(final SpacingImpl spaceProperty) {
    final Element result = new Element("Space");
    spaceProperty.refresh(this);
    result.setAttribute("minspace", String.valueOf(spaceProperty.getMinSpaces()));
    result.setAttribute("maxspace", String.valueOf(spaceProperty.getMaxSpaces()));
    result.setAttribute("minlf", String.valueOf(spaceProperty.getMinLineFeeds()));
    return result;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element save(final WrapImpl wrap) {
    final Element result = new Element("Wrap");
    result.setAttribute("type", wrap.getType().toString());
    result.setAttribute("id", wrap.getId());
    return result;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element save(final IndentImpl indent) {
    final Element element = new Element("Indent");
    element.setAttribute("type", indent.getType().toString());
    return element;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Element save(final AlignmentImpl alignment) {
    final Element result = new Element("Alignment");
    result.setAttribute("id", alignment.getId());
    return result;
  }

  public void format(FormattingModel model) {
    formatWithoutRealModifications();
    performModifications(model);
  }

  public void formatWithoutRealModifications() {
    while (true) {
      myAlignAgain.clear();
      myCurrentBlock = myFirstTokenBlock;
      while (myCurrentBlock != null) {
        processToken();
      }
      if (myAlignAgain.isEmpty()) return;
      reset();
    }
  }

  private void reset() {
    myAlignedAlignments.clear();
    myPreviousDependancies.clear();
    myWrapCandidate = null;
    for (AbstractBlockWrapper blockWrapper : myInfos.values()) {
      blockWrapper.reset();
    }
  }

  public void performModifications(FormattingModel model) {

    List<LeafBlockWrapper> blocksToModify = collectBlocksToModify();

    int shift = 0;

    try {
      for (LeafBlockWrapper block : blocksToModify) {
        shift = replaceWhiteSpace(model, block, shift, block.getWhiteSpace().generateWhiteSpace(myIndentOption));
      }
    }
    finally {
      model.commitChanges();
    }
  }

  protected int replaceWhiteSpace(final FormattingModel model,
                                  @NotNull final LeafBlockWrapper block,
                                  int shift,
                                  final String newWhiteSpace) {
    final WhiteSpace whiteSpace = block.getWhiteSpace();
    final TextRange textRange = whiteSpace.getTextRange();
    final TextRange wsRange = shiftRange(textRange, shift);
    TextRange newWhiteSpaceRange = model.replaceWhiteSpace(wsRange, newWhiteSpace);

    shift += (newWhiteSpaceRange.getLength() - (textRange.getLength()));

    if (block.isLeaf() && whiteSpace.containsLineFeeds() && block.containsLineFeeds()) {
      final TextRange currentBlockRange = shiftRange(block.getTextRange(), shift);

      IndentInside lastLineIndent = block.getLastLineIndent();
      IndentInside whiteSpaceIndent = IndentInside.createIndentOn(IndentInside.getLastLine(newWhiteSpace));
      final int shiftInside = calcShift(lastLineIndent, whiteSpaceIndent);

      final TextRange newBlockRange = model.shiftIndentInsideRange(currentBlockRange, shiftInside);
      shift += newBlockRange.getLength() - block.getTextRange().getLength();
    }
    return shift;
  }

  private List<LeafBlockWrapper> collectBlocksToModify() {
    List<LeafBlockWrapper> blocksToModify = new ArrayList<LeafBlockWrapper>();

    for (LeafBlockWrapper block = myFirstTokenBlock; block != null; block = block.getNextBlock()) {
      final WhiteSpace whiteSpace = block.getWhiteSpace();
      if (!whiteSpace.isReadOnly()) {
        final String newWhiteSpace = whiteSpace.generateWhiteSpace(myIndentOption);
        if (!whiteSpace.equalsToString(newWhiteSpace)) {
          blocksToModify.add(block);
        }
      }
    }
    return blocksToModify;
  }

  private static TextRange shiftRange(final TextRange textRange, final int shift) {
    return new TextRange(textRange.getStartOffset() + shift, textRange.getEndOffset() + shift);
  }

  private AbstractBlockWrapper getBlockInfo(final Block rootBlock) {
    if (rootBlock == null) return null;
    return myInfos.get(rootBlock);
  }

  private void processToken() {

    final SpacingImpl spaceProperty = myCurrentBlock.getSpaceProperty();
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

    if (!whiteSpace.isIsReadOnly() && shouldReformatBecauseOfBackwardDependance(whiteSpace.getTextRange())) {
      myAlignAgain.add(whiteSpace);
    }
    else {
      myAlignAgain.remove(whiteSpace);
    }

    myCurrentBlock = myCurrentBlock.getNextBlock();
  }

  private boolean shouldReformatBecauseOfBackwardDependance(TextRange changed) {
    for (TextRange textRange : myPreviousDependancies.keySet()) {
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

  private void saveDependancy(final SpacingImpl spaceProperty) {
    final DependantSpacingImpl dependantSpaceProperty = ((DependantSpacingImpl)spaceProperty);
    final TextRange dependancy = dependantSpaceProperty.getDependancy();
    if (dependantSpaceProperty.wasLFUsed()) {
      myPreviousDependancies.put(dependancy,
                                 new Pair<AbstractBlockWrapper, Boolean>(myCurrentBlock, Boolean.TRUE));
    }
    else {
      final boolean value = containsLineFeeds(dependancy);
      if (value) {
        dependantSpaceProperty.setLFWasUsed(true);
      }
      myPreviousDependancies.put(dependancy,
                                 new Pair<AbstractBlockWrapper, Boolean>(myCurrentBlock, value));
    }
  }

  private static boolean shouldSaveDependancy(final SpacingImpl spaceProperty, WhiteSpace whiteSpace) {
    if (!(spaceProperty instanceof DependantSpacingImpl)) return false;

    final TextRange dependancy = ((DependantSpacingImpl)spaceProperty).getDependancy();
    return whiteSpace.getTextRange().getStartOffset() < dependancy.getEndOffset();
  }

  private boolean processWrap(Spacing spacing) {
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
    final TextRange textRange = myCurrentBlock.getTextRange();
    final WrapImpl[] wraps = myCurrentBlock.getWraps();

    boolean wrapWasPresent = whiteSpace.containsLineFeeds();

    if (wrapWasPresent) {
      myFirstWrappedBlockOnLine = null;
    }

    if (whiteSpace.containsLineFeeds() && !whiteSpace.containsLineFeedsInitially()) {
      whiteSpace.removeLineFeeds(spacing, this);
    }

    boolean wrapIsPresent = whiteSpace.containsLineFeeds();

    for (WrapImpl wrap : wraps) {
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
        if (!wrapWasPresent && wrap != null) {
          if (myFirstWrappedBlockOnLine != null && wrap.isChildOf(myFirstWrappedBlockOnLine.getWrap())) {
            wrap.ignoreParentWrap(myFirstWrappedBlockOnLine.getWrap());
            myCurrentBlock = myFirstWrappedBlockOnLine;
            return true;
          }
          else {
            myFirstWrappedBlockOnLine = myCurrentBlock;
          }
        }
      }

      myWrapCandidate = null;
    }
    else {
      for (WrapImpl wrap1 : wraps) {
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
    return wrap == currentWrap || !wrap.isChildOf(currentWrap);
  }

  private boolean isCandidateToBeWrapped(final WrapImpl wrap) {
    return isSuitableInTheCurrentPosition(wrap) &&
           (wrap.getType() == WrapImpl.Type.WRAP_AS_NEEDED || wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED) &&
           !myCurrentBlock.getWhiteSpace().isReadOnly();
  }

  private void onCurrentLineChanged() {
    myAlignedAlignments.clear();
    myWrapCandidate = null;
  }

  private void adjustLineIndent() {
    IndentData alignOffset = getAlignOffset();
    if (alignOffset == null) {
      final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
      final IndentData offset = myCurrentBlock.calculateOffset(myIndentOption);
      whiteSpace.setSpaces(offset.getSpaces(), offset.getIndentSpaces());
    }
    else {
      final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
      whiteSpace.setSpaces(alignOffset.getSpaces(), alignOffset.getIndentSpaces());
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

    return wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED && lineOver() && positionAfterWrappingIsSutable();
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
    for (WrapImpl wrap : wraps) {
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
    return !myCurrentBlock.containsLineFeeds() &&
           getOffsetBefore(myCurrentBlock.getBlock()) + myCurrentBlock.getTextRange().getLength() > mySettings.RIGHT_MARGIN;
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

  private IndentData getAlignOffset() {
    AbstractBlockWrapper current = myCurrentBlock;
    while (true) {
      final AlignmentImpl alignment = (AlignmentImpl)current.getBlock().getAlignment();
      if (alignment != null && alignment.getOffsetRespBlockBefore(myCurrentBlock) != null) {
        final LeafBlockWrapper block = alignment.getOffsetRespBlockBefore(myCurrentBlock);
        final WhiteSpace whiteSpace = block.getWhiteSpace();
        if (whiteSpace.containsLineFeeds()) {
          return new IndentData(whiteSpace.getIndentSpaces(), whiteSpace.getSpaces());
        }
        else {
          final int offsetBeforeBlock = getOffsetBefore(block.getBlock());
          final AbstractBlockWrapper prevIndentedBlock = getPreviousIndentedBlock();
          if (prevIndentedBlock == null) {
            return new IndentData(0, offsetBeforeBlock);
          }
          else {
            final int parentIndent = prevIndentedBlock.getWhiteSpace().getIndentOffset();
            if (parentIndent > offsetBeforeBlock) {
              return new IndentData(0, offsetBeforeBlock);
            }
            else {
              return new IndentData(parentIndent, offsetBeforeBlock - parentIndent);
            }
          }
        }

      }
      else {
        current = current.getParent();
        if (current == null) return null;
        if (current.getStartOffset() != myCurrentBlock.getStartOffset()) return null;
      }
    }
  }

  public boolean containsLineFeeds(final TextRange dependance) {
    LeafBlockWrapper child = myTextRangeToWrapper.get(dependance.getStartOffset());
    if (child == null) return false;
    if (child.containsLineFeeds()) return true;
    final int endOffset = dependance.getEndOffset();
    while (child.getTextRange().getEndOffset() < endOffset) {
      child = child.getNextBlock();
      if (child.getWhiteSpace().containsLineFeeds()) return true;
      if (child.containsLineFeeds()) return true;
    }
    return false;
  }

  public LeafBlockWrapper getBlockAfter(final int startOffset) {
    int current = startOffset;
    LeafBlockWrapper result = null;
    while (current < myLastWhiteSpace.getTextRange().getStartOffset()) {
      final LeafBlockWrapper currentValue = myTextRangeToWrapper.get(current);
      if (currentValue != null) {
        result = currentValue;
        break;
      }
      current++;
    }

    LeafBlockWrapper prevBlock = getPrevBlock(result);

    if (prevBlock != null && prevBlock.contains(startOffset)) {
      return prevBlock;
    }
    else {
      return result;
    }
  }

  private LeafBlockWrapper getPrevBlock(final LeafBlockWrapper result) {
    if (result != null) {
      return result.getPreviousBlock();
    }
    else {
      return myLastTokenBlock;
    }
  }

  public void setAllWhiteSpacesAreReadOnly() {
    LeafBlockWrapper current = myFirstTokenBlock;
    while (current != null) {
      current.getWhiteSpace().setReadOnly(true);
      current = current.getNextBlock();
    }
  }

  static class ChildAttributesInfo {
    public Block parent;
    ChildAttributes attributes;
    int index;

    public ChildAttributesInfo(final Block parent, final ChildAttributes attributes, final int index) {
      this.parent = parent;
      this.attributes = attributes;
      this.index = index;
    }
  }

  public IndentInfo getIndentAt(final int offset) {
    processBlocksBefore(offset);
    AbstractBlockWrapper parent = getParentFor(offset, myCurrentBlock);
    if (parent == null) return new IndentInfo(0, 0, 0);
    int index = getNewChildPosition(parent, offset);
    final Block block = parent.getBlock();

    ChildAttributesInfo info = getChildAttributesInfo(block, index);


    return adjustLineIndent(myInfos.get(info.parent), info.attributes, info.index);
  }

  private static ChildAttributesInfo getChildAttributesInfo(final Block block, final int index) {
    ChildAttributes childAttributes = block.getChildAttributes(index);

    if (childAttributes == ChildAttributes.DELEGATE_TO_PREV_CHILD) {
      final Block newBlock = block.getSubBlocks().get(index - 1);
      return getChildAttributesInfo(newBlock, newBlock.getSubBlocks().size());

    }

    else if (childAttributes == ChildAttributes.DELEGATE_TO_NEXT_CHILD) {
      return getChildAttributesInfo(block.getSubBlocks().get(index), 0);
    }

    else {
      return new ChildAttributesInfo(block, childAttributes, index);
    }

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
    final LeafBlockWrapper alignRespBlock = ((AlignmentImpl)alignment).getOffsetRespBlockBefore(blockAfter);
    if (alignRespBlock != null) {
      return getOffsetBefore(alignRespBlock.getBlock());
    }
    else {
      return -1;
    }
  }

  private int getNewChildPosition(final AbstractBlockWrapper parent, final int offset) {
    final List<Block> subBlocks = parent.getBlock().getSubBlocks();
    for (int i = 0; i < subBlocks.size(); i++) {
      Block block = subBlocks.get(i);
      if (myInfos.get(block).getTextRange().getStartOffset() >= offset) return i;
    }
    return subBlocks.size();
  }

  private static AbstractBlockWrapper getParentFor(final int offset, AbstractBlockWrapper block) {
    AbstractBlockWrapper current = block;
    while (current != null) {
      final TextRange textRange = current.getTextRange();
      if (textRange.getStartOffset() < offset && textRange.getEndOffset() >= offset) {
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
    if (block == null) {
      if (myLastTokenBlock.getBlock().isIncomplete()) {
        return myLastTokenBlock.getBlock();
      }
      else {
        return null;
      }
    }

    AbstractBlockWrapper current = block;
    while (current.getParent() != null && current.getParent().getStartOffset() > offset) {
      current = current.getParent();
    }
    if (current.getParent() == null) return null;

    final List<Block> subBlocks = current.getParent().getBlock().getSubBlocks();
    final int index = subBlocks.indexOf(current.getBlock());
    if (index < 0) {
      LOG.assertTrue(false, current.getParent().getBlock().getClass().getName());
    }
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

  private static Block getLastChildOf(final Block currentResult) {
    final List<Block> subBlocks = currentResult.getSubBlocks();
    if (subBlocks.isEmpty()) return null;
    return subBlocks.get(subBlocks.size() - 1);
  }

  private void processBlocksBefore(final int offset) {
    while (true) {
      myAlignAgain.clear();
      myCurrentBlock = myFirstTokenBlock;
      while (myCurrentBlock != null && myCurrentBlock.getStartOffset() < offset) {
        processToken();
        if (myCurrentBlock == null) {
          myCurrentBlock = myLastTokenBlock;
          break;
        }
      }
      if (myAlignAgain.isEmpty()) return;
      reset();
    }
  }

  public LeafBlockWrapper getFirstTokenBlock() {
    return myFirstTokenBlock;
  }

  public WhiteSpace getLastWhiteSpace() {
    return myLastWhiteSpace;
  }

  private int calcShift(final IndentInside lastLineIndent, final IndentInside whiteSpaceIndent) {
    final CodeStyleSettings.IndentOptions options = mySettings.JAVA_INDENT_OPTIONS;
    if (lastLineIndent.equals(whiteSpaceIndent)) return 0;
    if (options.USE_TAB_CHARACTER) {
      if (lastLineIndent.whiteSpaces > 0) {
        return whiteSpaceIndent.getSpacesCount(options);
      }
      else {
        return whiteSpaceIndent.tabs - lastLineIndent.tabs;
      }
    }
    else {
      if (lastLineIndent.tabs > 0) {
        return whiteSpaceIndent.getTabsCount(options);
      }
      else {
        return whiteSpaceIndent.whiteSpaces - lastLineIndent.whiteSpaces;
      }
    }
  }


  public static boolean previousBlockIsComplete(final LeafBlockWrapper blockAfterOffset) {

    AbstractBlockWrapper current = blockAfterOffset.getPreviousBlock();

    if (current == null) return true;

    while (current.getParent() != null && current.getParent().getEndOffset() == current.getEndOffset()) {
      if (current.getBlock().isIncomplete()) return false;
      current = current.getParent();
    }

    return true;
  }
}
