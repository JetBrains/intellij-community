package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.IndentInfo;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.util.IncorrectOperationException;

public class FormatterImpl extends Formatter implements ApplicationComponent{
  public Alignment createAlignment() {
    return new AlignmentImpl(AlignmentImpl.Type.NORMAL);
  }

  public Indent createNormalIndent() {
    return new IndentImpl(IndentImpl.Type.NORMAL, false);
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
    return new SpacePropertyImpl(0,0,0,true, false, true, 0);
  }

  public SpaceProperty createDependentLFProperty(int minOffset, int maxOffset, TextRange dependence, boolean keepLineBreaks,
                                                 int keepBlankLines) {
    return new DependantSpacePropertyImpl(minOffset, maxOffset, dependence, keepLineBreaks, keepBlankLines);
  }

  public void format(FormattingModel model,
                     Block rootBlock,
                     CodeStyleSettings settings,
                     CodeStyleSettings.IndentOptions indentOptions,
                     TextRange affectedRange) throws IncorrectOperationException {
    new FormatProcessor(model, rootBlock, settings, indentOptions, affectedRange).format();
  }

  public IndentInfo getWhiteSpaceBefore(final PsiBasedFormattingModel model,
                                        final Block block,
                                        final CodeStyleSettings settings,
                                        final CodeStyleSettings.IndentOptions indentOptions,
                                        final TextRange affectedRange, final boolean mayChangeLineFeeds) {
    final FormatProcessor processor = new FormatProcessor(model, block, settings, indentOptions, affectedRange);
    WhiteSpace whiteSpace = processor.getWhiteSpaceBefore(affectedRange.getStartOffset());
    if (!mayChangeLineFeeds) {
      whiteSpace.setLineFeedsAreReadOnly();
    }
    processor.setAllWhiteSpacesAreReadOnly();
    whiteSpace.setReadOnly(false);
    processor.formatWithoutRealModifications();
    return new IndentInfo(whiteSpace.getLineFeeds(), whiteSpace.getIndentOffset(), whiteSpace.getSpaces());
  }

  public Indent createSpaceIndent(final int spaces) {
    return new IndentImpl(IndentImpl.Type.SPACES, false, spaces);
  }

  public Indent createAbsoluteLabelIndent() {
    return new IndentImpl(IndentImpl.Type.LABEL, true);
  }

  public SpaceProperty createSafeSpace(final boolean shouldKeepLineBreaks, final int keepBlankLines) {
    return new SpacePropertyImpl(0,0,0,false,true, shouldKeepLineBreaks, keepBlankLines);
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
}
