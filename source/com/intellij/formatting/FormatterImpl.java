package com.intellij.formatting;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class FormatterImpl extends FormatterEx
  implements ApplicationComponent,
             IndentFactory,
             WrapFactory,
             AlignmentFactory,
             SpacingFactory,
             FormattingModelFactory
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormatterImpl");

  private int myIsDisabledCount = 0;
  private final IndentImpl NONE_INDENT = new IndentImpl(IndentImpl.Type.NONE, false);
  private final IndentImpl myAbsoluteNoneIndent = new IndentImpl(IndentImpl.Type.NONE, true);
  private final IndentImpl myLabelIndent = new IndentImpl(IndentImpl.Type.LABEL, false);
  private final IndentImpl myContinuationIndent = new IndentImpl(IndentImpl.Type.CONTINUATION, false);
  private final IndentImpl myContinutationWithoutFirstIndent = new IndentImpl(IndentImpl.Type.CONTINUATION_WITHOUT_FIRST, false);
  private final IndentImpl myAbsoluteLabelIndent = new IndentImpl(IndentImpl.Type.LABEL, true);
  private final IndentImpl myNormalIndent = new IndentImpl(IndentImpl.Type.NORMAL, false);
  private final SpacingImpl myReadOnlySpacing = new SpacingImpl(0, 0, 0, true, false, true, 0, false);

  public FormatterImpl() {
    Indent.setFactory(this);
    Wrap.setFactory(this);
    Alignment.setFactory(this);
    Spacing.setFactory(this);
    FormattingModelProvider.setFactory(this);
  }

  public Alignment createAlignment() {
    return new AlignmentImpl(AlignmentImpl.Type.NORMAL);
  }

  public Indent getNormalIndent() {
    return myNormalIndent;
  }

  public Indent getNoneIndent() {
    return NONE_INDENT;
  }

  public Wrap createWrap(WrapType type, boolean wrapFirstElement) {
    return new WrapImpl(type, wrapFirstElement);
  }

  public Wrap createChildWrap(final Wrap parentWrap, final WrapType wrapType, final boolean wrapFirstElement) {
    final WrapImpl result = new WrapImpl(wrapType, wrapFirstElement);
    result.registerParent((WrapImpl)parentWrap);
    return result;
  }

  public Spacing createSpacing(int minOffset,
                               int maxOffset,
                               int minLineFeeds,
                               final boolean keepLineBreaks,
                               final int keepBlankLines) {
    return getSpacingImpl(minOffset, maxOffset, minLineFeeds, false, false, keepLineBreaks, keepBlankLines,false);
  }

  public Spacing getReadOnlySpacing() {
    return myReadOnlySpacing;
  }

  public Spacing createDependentLFSpacing(int minOffset, int maxOffset, TextRange dependence, boolean keepLineBreaks,
                                          int keepBlankLines) {
    return new DependantSpacingImpl(minOffset, maxOffset, dependence, keepLineBreaks, keepBlankLines);
  }

  public void format(FormattingModel model,
                     CodeStyleSettings settings,
                     CodeStyleSettings.IndentOptions indentOptions,
                     TextRange affectedRange,
                     final boolean processHeadingWhitespace) throws IncorrectOperationException {
    disableFormatting();
    try {
      new FormatProcessor(model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, affectedRange, processHeadingWhitespace).format(model);
    } finally {
      enableFormatting();
    }

  }

  public void formatWithoutModifications(FormattingDocumentModel model,
                                         Block rootBlock,
                                         CodeStyleSettings settings,
                                         CodeStyleSettings.IndentOptions indentOptions,
                                         TextRange affectedRange) throws IncorrectOperationException {
    disableFormatting();
    try {
      new FormatProcessor(model, rootBlock, settings, indentOptions, affectedRange, true).formatWithoutRealModifications();
    } finally {
      enableFormatting();
    }

  }

  public IndentInfo getWhiteSpaceBefore(final FormattingDocumentModel model,
                                        final Block block,
                                        final CodeStyleSettings settings,
                                        final CodeStyleSettings.IndentOptions indentOptions,
                                        final TextRange affectedRange, final boolean mayChangeLineFeeds) {
    disableFormatting();
    try {
      final FormatProcessor processor = new FormatProcessor(model, block, settings, indentOptions, affectedRange, true);
      final LeafBlockWrapper blockBefore = processor.getBlockAfter(affectedRange.getStartOffset());
      LOG.assertTrue(blockBefore != null);
      WhiteSpace whiteSpace = blockBefore.getWhiteSpace();
      LOG.assertTrue(whiteSpace != null);
      if (!mayChangeLineFeeds) {
        whiteSpace.setLineFeedsAreReadOnly();
      }
      processor.setAllWhiteSpacesAreReadOnly();
      whiteSpace.setReadOnly(false);
      processor.formatWithoutRealModifications();
      return new IndentInfo(whiteSpace.getLineFeeds(), whiteSpace.getIndentOffset(), whiteSpace.getSpaces());
    } finally {
      enableFormatting();
    }

  }

  public void adjustLineIndentsForRange(final FormattingModel model,
                                        final CodeStyleSettings settings,
                                        final CodeStyleSettings.IndentOptions indentOptions,
                                        final TextRange rangeToAdjust) {
    disableFormatting();
    try {
      final FormattingDocumentModel documentModel = model.getDocumentModel();
      final Block block = model.getRootBlock();
      final FormatProcessor processor = new FormatProcessor(documentModel, block, settings, indentOptions, rangeToAdjust,
                                                            true);
      LeafBlockWrapper tokenBlock = processor.getFirstTokenBlock();
      while (tokenBlock != null) {
        final WhiteSpace whiteSpace = tokenBlock.getWhiteSpace();
        whiteSpace.setLineFeedsAreReadOnly(true);
        if (!whiteSpace.containsLineFeeds()) {
          whiteSpace.setIsReadOnly(true);
        }
        tokenBlock = tokenBlock.getNextBlock();
      }
      processor.formatWithoutRealModifications();
      processor.performModifications(model);
    }
    finally {
      enableFormatting();
    }

  }

  public void formatAroundRange(final FormattingModel model,
                                final CodeStyleSettings settings,
                                final TextRange textRange,
                                final FileType fileType) {
    disableFormatting();
    try {
      final FormattingDocumentModel documentModel = model.getDocumentModel();
      final Block block = model.getRootBlock();
      final FormatProcessor processor = new FormatProcessor(documentModel, block, settings,
                                                            settings.getIndentOptions(fileType), null, true);
      LeafBlockWrapper tokenBlock = processor.getFirstTokenBlock();
      while (tokenBlock != null) {
        final WhiteSpace whiteSpace = tokenBlock.getWhiteSpace();

        if (whiteSpace.getEndOffset() < textRange.getStartOffset()) {
          whiteSpace.setIsReadOnly(true);
        } else if (whiteSpace.getStartOffset() > textRange.getStartOffset() &&
                   whiteSpace.getEndOffset() < textRange.getEndOffset()){
          if (whiteSpace.containsLineFeeds()) {
            whiteSpace.setLineFeedsAreReadOnly(true);
          } else {
            whiteSpace.setIsReadOnly(true);
          }
        } else if (whiteSpace.getEndOffset() > textRange.getEndOffset() + 1) {
          whiteSpace.setIsReadOnly(true);
        }

        tokenBlock = tokenBlock.getNextBlock();
      }
      processor.formatWithoutRealModifications();
      processor.performModifications(model);

    } finally{
      enableFormatting();
    }
  }

  public int adjustLineIndent(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final int offset,
                              final TextRange affectedRange) throws IncorrectOperationException {
    disableFormatting();
    if (model instanceof PsiBasedFormattingModel) {
      ((PsiBasedFormattingModel)model).canModifyAllWhiteSpaces();
    }
    try {
      final FormattingDocumentModel documentModel = model.getDocumentModel();
      final Block block = model.getRootBlock();
      final FormatProcessor processor = new FormatProcessor(documentModel, block, settings, indentOptions, affectedRange,
                                                            true, offset);

      final LeafBlockWrapper blockAfterOffset = processor.getBlockAfter(offset);

      if (blockAfterOffset != null && blockAfterOffset.contains(offset)) {
        return offset;
      }

      if (blockAfterOffset != null) {
        return adjustLineIndent(offset, documentModel, processor, indentOptions, model, blockAfterOffset.getWhiteSpace());
      } else {
        return adjustLineIndent(offset, documentModel, processor, indentOptions, model, processor.getLastWhiteSpace());
      }
    } finally {
      enableFormatting();
    }
  }

  private static int adjustLineIndent(
    final int offset,
    final FormattingDocumentModel documentModel,
    final FormatProcessor processor,
    final CodeStyleSettings.IndentOptions indentOptions,
    final FormattingModel model,
    final WhiteSpace whiteSpace)
  {
    boolean wsContainsCaret = whiteSpace.getStartOffset() <= offset && whiteSpace.getEndOffset() > offset;

    final CharSequence text = getCharSequence(documentModel);
    int lineStartOffset = getLineStartOffset(offset, whiteSpace, text, documentModel);

    processor.setAllWhiteSpacesAreReadOnly();
    whiteSpace.setLineFeedsAreReadOnly(true);
    final IndentInfo indent;
    if (hasContentAfterLineBreak(documentModel, offset, whiteSpace) ) {
      whiteSpace.setReadOnly(false);
      processor.formatWithoutRealModifications();
      indent = new IndentInfo(0, whiteSpace.getIndentOffset(), whiteSpace.getSpaces());
    }
    else {
      indent = processor.getIndentAt(offset);
    }

    final String newWS = whiteSpace.generateWhiteSpace(indentOptions, lineStartOffset, indent).toString();
    if (!whiteSpace.equalsToString(newWS)) {
      try {
        model.replaceWhiteSpace(whiteSpace.getTextRange(), newWS);
      }
      finally {
        model.commitChanges();
      }
    }

    if (wsContainsCaret) {
      return whiteSpace.getStartOffset()
             + CharArrayUtil.shiftForward(newWS, lineStartOffset - whiteSpace.getStartOffset(), " \t");
    } else {
      return offset - whiteSpace.getLength() + newWS.length();
    }
  }

  private static boolean hasContentAfterLineBreak(final FormattingDocumentModel documentModel, final int offset, final WhiteSpace whiteSpace) {
    return documentModel.getLineNumber(offset) == documentModel.getLineNumber(whiteSpace.getEndOffset()) &&
           documentModel.getTextLength() != offset;
  }

  public String getLineIndent(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final int offset,
                              final TextRange affectedRange) {
      final FormattingDocumentModel documentModel = model.getDocumentModel();
      final Block block = model.getRootBlock();
      final FormatProcessor processor = new FormatProcessor(documentModel, block, settings, indentOptions, affectedRange,
                                                            true, offset);

      final LeafBlockWrapper blockAfterOffset = processor.getBlockAfter(offset);

      if (blockAfterOffset != null) {
        final WhiteSpace whiteSpace = blockAfterOffset.getWhiteSpace();
        processor.setAllWhiteSpacesAreReadOnly();
        whiteSpace.setLineFeedsAreReadOnly(true);
        final IndentInfo indent;
        if (hasContentAfterLineBreak(documentModel, offset, whiteSpace)) {
          whiteSpace.setReadOnly(false);
          processor.formatWithoutRealModifications();
          indent = new IndentInfo(0, whiteSpace.getIndentOffset(), whiteSpace.getSpaces());
        }
        else {
          indent = processor.getIndentAt(offset);
        }

        return indent.generateNewWhiteSpace(indentOptions).toString();

      } else {
        return null;
      }
  }

  public static String getText(final FormattingDocumentModel documentModel) {
    return getCharSequence(documentModel).toString();
  }

  private static CharSequence getCharSequence(final FormattingDocumentModel documentModel) {
    return documentModel.getText(new TextRange(0, documentModel.getTextLength()));
  }

  private static int getLineStartOffset(final int offset,
                                        final WhiteSpace whiteSpace,
                                        final CharSequence text,
                                        final FormattingDocumentModel documentModel) {
    int lineStartOffset = offset;

    lineStartOffset = CharArrayUtil.shiftBackwardUntil(text, lineStartOffset, " \t\n");
    if (lineStartOffset > whiteSpace.getStartOffset()) {
      if (lineStartOffset >= text.length()) lineStartOffset = text.length() - 1;
      if (text.charAt(lineStartOffset) == '\n'
          && whiteSpace.getStartOffset() <= documentModel.getLineStartOffset(documentModel.getLineNumber(lineStartOffset - 1))) {
        lineStartOffset--;
      }
      lineStartOffset = CharArrayUtil.shiftBackward(text, lineStartOffset, "\t ");
      if (lineStartOffset < 0) lineStartOffset = 0;
      if (lineStartOffset != offset && text.charAt(lineStartOffset) == '\n') {
        lineStartOffset++;
      }
    }
    return lineStartOffset;
  }

  public void adjustTextRange(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final TextRange affectedRange,
                              final boolean keepBlankLines,
                              final boolean keepLineBreaks,
                              final boolean changeWSBeforeFirstElement,
                              final boolean changeLineFeedsBeforeFirstElement,
                              @Nullable final IndentInfoStorage indentInfoStorage) {
    disableFormatting();
    try {
      final FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, affectedRange,
                                                            true);
      LeafBlockWrapper current = processor.getFirstTokenBlock();
      while (current != null) {
        WhiteSpace whiteSpace = current.getWhiteSpace();

        if (!whiteSpace.isReadOnly()) {
          if (whiteSpace.getStartOffset() > affectedRange.getStartOffset()) {
            if (whiteSpace.containsLineFeeds() && indentInfoStorage != null) {
              whiteSpace.setLineFeedsAreReadOnly(true);
              current.setIndentFromParent(indentInfoStorage.getIndentInfo(current.getStartOffset()));
            } else {
              whiteSpace.setReadOnly(true);
            }
          } else {
            if (!changeWSBeforeFirstElement) {
              whiteSpace.setReadOnly(true);
            } else {
              if (!changeLineFeedsBeforeFirstElement) {
                whiteSpace.setLineFeedsAreReadOnly(true);
              }
              final SpacingImpl spaceProperty = current.getSpaceProperty();
              if (spaceProperty != null) {
                boolean needChange = false;
                int newKeepLineBreaks = spaceProperty.getKeepBlankLines();
                boolean newKeepLineBreaksFlag = spaceProperty.shouldKeepLineFeeds();

                if (!keepLineBreaks) {
                  needChange = true;
                  newKeepLineBreaksFlag = false;
                }
                if (!keepBlankLines) {
                  needChange = true;
                  newKeepLineBreaks = 0;
                }

                if (needChange) {
                  assert !(spaceProperty instanceof DependantSpacingImpl);
                  current.setSpaceProperty(
                    getSpacingImpl(
                      spaceProperty.getMinSpaces(), spaceProperty.getMaxSpaces(), spaceProperty.getMinLineFeeds(), spaceProperty.isReadOnly(),
                      spaceProperty.isSafe(), newKeepLineBreaksFlag, newKeepLineBreaks, false
                    )
                  );
                }
              }
            }
          }
        }
        current = current.getNextBlock();
      }
      processor.format(model);
    } finally {
      enableFormatting();
    }

  }

  public void adjustTextRange(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final TextRange affectedRange) {
    disableFormatting();
    try {
      final FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, affectedRange,
                                                            true);
      LeafBlockWrapper current = processor.getFirstTokenBlock();
      while (current != null) {
        WhiteSpace whiteSpace = current.getWhiteSpace();

        if (!whiteSpace.isReadOnly()) {
          if (whiteSpace.getStartOffset() > affectedRange.getStartOffset()) {
            whiteSpace.setReadOnly(true);
          } else {
            whiteSpace.setReadOnly(false);
          }
        }
        current = current.getNextBlock();
      }
      processor.format(model);
    } finally {
      enableFormatting();
    }

  }

  public void saveIndents(final FormattingModel model, final TextRange affectedRange,
                          IndentInfoStorage storage,
                          final CodeStyleSettings settings,
                          final CodeStyleSettings.IndentOptions indentOptions) {
    final Block block = model.getRootBlock();
    final FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), block, settings, indentOptions, affectedRange,
                                                          true);
    LeafBlockWrapper current = processor.getFirstTokenBlock();
    while (current != null) {
      WhiteSpace whiteSpace = current.getWhiteSpace();

      if (!whiteSpace.isReadOnly() && whiteSpace.containsLineFeeds()) {
        storage.saveIndentInfo(current.calcIndentFromParent(), current.getStartOffset());
      }
      current = current.getNextBlock();
    }
  }

  public FormattingModel createFormattingModelForPsiFile(final PsiFile file,
                                                         final Block rootBlock,
                                                         final CodeStyleSettings settings) {
    return new PsiBasedFormattingModel(file, rootBlock, FormattingDocumentModelImpl.createOn(file));
  }

  public Indent getSpaceIndent(final int spaces) {
    return new IndentImpl(IndentImpl.Type.SPACES, false, spaces);
  }

  public Indent getAbsoluteLabelIndent() {
    return myAbsoluteLabelIndent;
  }

  public Spacing createSafeSpacing(final boolean shouldKeepLineBreaks, final int keepBlankLines) {
    return getSpacingImpl(0, 0, 0, false, true, shouldKeepLineBreaks, keepBlankLines, false);
  }

  public Spacing createKeepingFirstColumnSpacing(final int minSpace,
                                                 final int maxSpace,
                                                 final boolean keepLineBreaks,
                                                 final int keepBlankLines) {
    return getSpacingImpl(minSpace, maxSpace, -1, false, false, keepLineBreaks, keepBlankLines, true);
  }

  private Map<SpacingImpl,SpacingImpl> ourSharedProperties = new HashMap<SpacingImpl,SpacingImpl>();
  private SpacingImpl ourSharedSpacing = new SpacingImpl(-1,-1,-1,false,false,false,-1,false);

  private SpacingImpl getSpacingImpl(final int minSpaces, final int maxSpaces, final int minLineFeeds, final boolean readOnly, final boolean safe,
                                     final boolean keepLineBreaksFlag,
                                     final int keepLineBreaks,
                                     final boolean keepFirstColumn) {
    synchronized(this) {
      ourSharedSpacing.init(minSpaces, maxSpaces, minLineFeeds, readOnly, safe, keepLineBreaksFlag, keepLineBreaks, keepFirstColumn);
      SpacingImpl spacing = ourSharedProperties.get(ourSharedSpacing);

      if (spacing == null) {
        spacing = new SpacingImpl(minSpaces, maxSpaces, minLineFeeds, readOnly, safe, keepLineBreaksFlag, keepLineBreaks, keepFirstColumn);
        ourSharedProperties.put(spacing, spacing);
      }
      return spacing;
    }
  }

  @NotNull
  public String getComponentName() {
    return "FormatterEx";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public Indent getAbsoluteNoneIndent() {
    return myAbsoluteNoneIndent;
  }

  public Indent getLabelIndent() {
    return myLabelIndent;
  }

  public Indent getContinuationIndent() {
    return myContinuationIndent;
  }

  public Indent getContinuationWithoutFirstIndent()//is default
  {
    return myContinutationWithoutFirstIndent;
  }

  public static int getLineFeedsToModified(final FormattingDocumentModel model, final int offset, final int startOffset) {
    return model.getLineNumber(offset) - model.getLineNumber(startOffset);
  }

  private final Object DISABLING_LOCK = new Object();

  public boolean isDisabled() {
    synchronized (DISABLING_LOCK) {
      return myIsDisabledCount > 0;
    }
  }

  public void disableFormatting() {
    synchronized (DISABLING_LOCK) {
      myIsDisabledCount++;
    }
  }

  public void enableFormatting() {
    synchronized (DISABLING_LOCK) {
      if (myIsDisabledCount <= 0) {
        LOG.error("enableFormatting()/disableFormatting() not paired. DisabledLevel = " + myIsDisabledCount);
      }
      myIsDisabledCount--;
    }
  }
}
