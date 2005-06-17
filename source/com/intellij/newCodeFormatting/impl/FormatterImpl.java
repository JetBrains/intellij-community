package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

public class FormatterImpl extends Formatter implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.newCodeFormatting.impl.FormatterImpl");

  private int myIsActive = 0;

  public Alignment createAlignment() {
    return new AlignmentImpl(AlignmentImpl.Type.NORMAL);
  }

  public Indent createNormalIndent() {
    return new IndentImpl(IndentImpl.Type.NORMAL, false);
  }

  public Indent createNormalIndent(int count) {
    final IndentImpl result = new IndentImpl(IndentImpl.Type.NORMAL, false);
    result.setCount(count);
    return result;
  }

  public Indent getNoneIndent() {
    return new IndentImpl(IndentImpl.Type.NONE, false);
  }

  public Wrap createWrap(int type, boolean wrapFirstElement) {
    return new WrapImpl(type, wrapFirstElement);
  }

  public Wrap createChildWrap(final Wrap parentWrap, final int wrapType, final boolean wrapFirstElement) {
    final WrapImpl result = new WrapImpl(wrapType, wrapFirstElement);
    result.registerParent((WrapImpl)parentWrap);
    return result;
  }

  public SpaceProperty createSpaceProperty(int minOffset,
                                           int maxOffset,
                                           int minLineFeeds,
                                           final boolean keepLineBreaks,
                                           final int keepBlankLines) {
    return new SpacePropertyImpl(minOffset, maxOffset, minLineFeeds, false, false, keepLineBreaks, keepBlankLines);
  }

  public SpaceProperty getReadOnlySpace() {
    return new SpacePropertyImpl(0, 0, 0, true, false, true, 0);
  }

  public SpaceProperty createDependentLFProperty(int minOffset, int maxOffset, TextRange dependence, boolean keepLineBreaks,
                                                 int keepBlankLines) {
    return new DependantSpacePropertyImpl(minOffset, maxOffset, dependence, keepLineBreaks, keepBlankLines);
  }

  public void format(FormattingModel model, CodeStyleSettings settings,
                     CodeStyleSettings.IndentOptions indentOptions,
                     TextRange affectedRange) throws IncorrectOperationException {
    myIsActive++;
    try {
      new FormatProcessor(model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, affectedRange).format(model);
    } finally {
      myIsActive--;
    }

  }

  public void formatWithoutModifications(FormattingDocumentModel model,
                                         Block rootBlock,
                                         CodeStyleSettings settings,
                                         CodeStyleSettings.IndentOptions indentOptions,
                                         TextRange affectedRange) throws IncorrectOperationException {
    myIsActive++;
    try {
      new FormatProcessor(model, rootBlock, settings, indentOptions, affectedRange).formatWithoutRealModifications();
    } finally {
      myIsActive--;
    }

  }

  public IndentInfo getWhiteSpaceBefore(final FormattingDocumentModel model,
                                        final Block block,
                                        final CodeStyleSettings settings,
                                        final CodeStyleSettings.IndentOptions indentOptions,
                                        final TextRange affectedRange, final boolean mayChangeLineFeeds) {
    myIsActive++;
    try {
      final FormatProcessor processor = new FormatProcessor(model, block, settings, indentOptions, affectedRange);
      final LeafBlockWrapper blockBefore = processor.getBlockBefore(affectedRange.getStartOffset());
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
      myIsActive--;
    }

  }

  public int adjustLineIndent(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final int offset,
                              final TextRange affectedRange) throws IncorrectOperationException {
    myIsActive++;
    try {
      myIsActive++;
      try {
        final FormattingDocumentModel documentModel = model.getDocumentModel();
        final Block block = model.getRootBlock();
        final FormatProcessor processor = new FormatProcessor(documentModel, block, settings, indentOptions, affectedRange);

        final LeafBlockWrapper blockAfterOffset = processor.getBlockBefore(offset);

        if (blockAfterOffset != null) {
          final WhiteSpace whiteSpace = blockAfterOffset.getWhiteSpace();
          boolean wsContainsCaret = whiteSpace.getTextRange().getStartOffset() <= offset && whiteSpace.getTextRange().getEndOffset() > offset;

          final CharSequence text = getCharSequence(documentModel);
          int lineStartOffset = getLineStartOffset(offset, whiteSpace, text);

          processor.setAllWhiteSpacesAreReadOnly();
          whiteSpace.setLineFeedsAreReadOnly(true);
          final IndentInfo indent;
          if (documentModel.getLineNumber(offset) == documentModel.getLineNumber(whiteSpace.getTextRange().getEndOffset())) {
            whiteSpace.setReadOnly(false);
            processor.formatWithoutRealModifications();
            indent = new IndentInfo(0, whiteSpace.getIndentOffset(), whiteSpace.getSpaces());
          }
          else {
            indent = processor.getIndentAt(offset);
          }

          final String newWS = whiteSpace.generateWhiteSpace(indentOptions, lineStartOffset, indent);
          model.replaceWhiteSpace(whiteSpace.getTextRange(), newWS, blockAfterOffset.getTextRange().getLength());


          if (wsContainsCaret) {
            return whiteSpace.getTextRange().getStartOffset()
                   + CharArrayUtil.shiftForward(newWS.toCharArray(), lineStartOffset - whiteSpace.getTextRange().getStartOffset(), " \t");
          } else {
            return offset - whiteSpace.getTextRange().getLength() + newWS.length();
          }
        } else {
          WhiteSpace lastWS = processor.getLastWhiteSpace();
          int lineStartOffset = getLineStartOffset(offset, lastWS, getText(documentModel));

          final IndentInfo indent = new IndentInfo(0, 0, 0);
          final String newWS = lastWS.generateWhiteSpace(indentOptions, lineStartOffset, indent);
          model.replaceWhiteSpace(lastWS.getTextRange(), newWS, 0);

          return lastWS.getTextRange().getStartOffset()
                 + CharArrayUtil.shiftForward(newWS.toCharArray(), lineStartOffset - lastWS.getTextRange().getStartOffset(), " \t");
        }
      } finally {
        myIsActive--;
      }
    } finally {
      myIsActive--;
    }
  }

  public static String getText(final FormattingDocumentModel documentModel) {
    return getCharSequence(documentModel).toString();
  }

  private static CharSequence getCharSequence(final FormattingDocumentModel documentModel) {
    return documentModel.getText(new TextRange(0, documentModel.getTextLength()));
  }

  private int getLineStartOffset(final int offset, final WhiteSpace whiteSpace, final CharSequence text) {
    int lineStartOffset = offset;
    lineStartOffset = CharArrayUtil.shiftBackwardUntil(text, lineStartOffset, " \t\n");
    if (lineStartOffset > whiteSpace.getTextRange().getStartOffset()) {
      if (text.charAt(lineStartOffset) == '\n') {
        lineStartOffset--;
      }
      lineStartOffset = CharArrayUtil.shiftBackward(text, lineStartOffset, "\t ");
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
                              final boolean changeLineFeedsBeforeFirstElement, final IndentInfoStorage indentInfoStorage) {
    myIsActive++;
    try {
      Block block = model.getRootBlock();
      final FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), block, settings, indentOptions, affectedRange);
      LeafBlockWrapper current = processor.getFirstTokenBlock();
      while (current != null) {
        WhiteSpace whiteSpace = current.getWhiteSpace();

        if (!whiteSpace.isReadOnly()) {
          if (whiteSpace.getTextRange().getStartOffset() > affectedRange.getStartOffset()) {
            if (whiteSpace.containsLineFeeds()) {
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
              final SpacePropertyImpl spaceProperty = current.getSpaceProperty();
              if (spaceProperty != null) {
                if (!keepLineBreaks) {
                  spaceProperty.setKeepLineBreaks(false);
                }
                if (!keepBlankLines) {
                  spaceProperty.setKeepLineBreaks(0);
                }
              }
            }
          }
        }
        current = current.getNextBlock();
      }
      processor.format(model);
    } finally {
      myIsActive--;
    }

  }

  public void saveIndents(final FormattingModel model, final TextRange affectedRange,
                          IndentInfoStorage storage,
                          final CodeStyleSettings settings,
                          final CodeStyleSettings.IndentOptions indentOptions) {
    final Block block = model.getRootBlock();
    final FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), block, settings, indentOptions, affectedRange);
    LeafBlockWrapper current = processor.getFirstTokenBlock();
    while (current != null) {
      WhiteSpace whiteSpace = current.getWhiteSpace();

      if (!whiteSpace.isReadOnly() && whiteSpace.containsLineFeeds()) {
        //if (whiteSpace.getTextRange().getStartOffset() > affectedRange.getStartOffset()) {
          storage.saveIndentInfo(current.calcIndentFromParent(), current.getTextRange().getStartOffset());
        //}        
      }
      current = current.getNextBlock();
    }
  }

  public FormattingModel createFormattingModelForPsiFile(final PsiFile file,
                                                         final Block rootBlock,
                                                         final CodeStyleSettings settings) {
    return new PsiBasedFormattingModel(file, settings, rootBlock);
  }

  public boolean isActive() {
    return myIsActive > 0;
  }


  public Indent createSpaceIndent(final int spaces) {
    return new IndentImpl(IndentImpl.Type.SPACES, false, spaces);
  }

  public Indent createAbsoluteLabelIndent() {
    return new IndentImpl(IndentImpl.Type.LABEL, true);
  }

  public SpaceProperty createSafeSpace(final boolean shouldKeepLineBreaks, final int keepBlankLines) {
    return new SpacePropertyImpl(0, 0, 0, false, true, shouldKeepLineBreaks, keepBlankLines);
  }

  public SpaceProperty createKeepingFirstLineSpaceProperty(final int minSpace,
                                                           final int maxSpace,
                                                           final boolean keepLineBreaks,
                                                           final int keepBlankLines) {
    final SpacePropertyImpl result = new SpacePropertyImpl(minSpace, maxSpace, -1, false, false, keepLineBreaks, keepBlankLines);
    result.setKeepFirstColumn(true);
    return result;
  }

  public String getComponentName() {
    return "Formatter";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public Indent createAbsoluteNormalIndent() {
    return new IndentImpl(IndentImpl.Type.NORMAL, true);
  }

  public Indent createAbsoluteNoneIndent() {
    return new IndentImpl(IndentImpl.Type.NONE, true);
  }

  public Indent createLabelIndent() {
    return new IndentImpl(IndentImpl.Type.LABEL, false);
  }

  public Indent createContinuationIndent() {
    return new IndentImpl(IndentImpl.Type.CONTINUATION, false);
  }

  public Indent createContinuationWithoutFirstIndent()//is default
  {
    return new IndentImpl(IndentImpl.Type.CONTINUATION_WITHOUT_FIRST, false);
  }

  public static int getLineFeedsToModified(final FormattingDocumentModel model, final int offset, final int startOffset) {
    return model.getLineNumber(offset) - model.getLineNumber(startOffset);
  }
}
