package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.components.ApplicationComponent;

public class FormatterImpl extends Formatter implements ApplicationComponent{
  public Alignment createAlignment() {
    return new AlignmentImpl(AlignmentImpl.Type.NORMAL);
  }

  public Indent getNormalIndent(int indentCount) {
    return new IndentImpl(IndentImpl.Type.NORMAL, indentCount, 0);
  }

  public Indent getLabelIndent(int indentCount) {
    return new IndentImpl(IndentImpl.Type.LABEL, indentCount, 0);
  }

  public Indent getNoneIndent() {
    return new IndentImpl(IndentImpl.Type.NONE, 0,0);
  }

  public Wrap createWrap(int type, boolean wrapFirstElement) {
    return new WrapImpl(type, wrapFirstElement);
  }

  public SpaceProperty createSpaceProperty(int minOffset, int maxOffset, int minLineFeeds, int maxLineFeeds) {
    return new SpacePropertyImpl(minOffset, maxOffset, minLineFeeds, maxLineFeeds, false);
  }

  public SpaceProperty getReadOnlySpace() {
    return new SpacePropertyImpl(0,0,0,0,true);
  }

  public SpaceProperty getAnySpace() {
    return new SpacePropertyImpl(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, false);
  }

  public void format(FormattingModel model,
                     Block rootBlock,
                     CodeStyleSettings settings,
                     CodeStyleSettings.IndentOptions indentOptions,
                     TextRange affectedRange) {
    new FormatProcessor(model, rootBlock, settings, indentOptions, affectedRange).format();
  }

  public String getComponentName() {
    return "Formatter";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
