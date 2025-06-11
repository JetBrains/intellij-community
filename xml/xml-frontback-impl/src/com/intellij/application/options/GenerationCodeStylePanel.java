// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CommenterForm;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBInsets;
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GenerationCodeStylePanel extends CodeStyleAbstractPanel {

  private final CommenterForm myCommenterForm;

  public GenerationCodeStylePanel(@NotNull CodeStyleSettings settings, Language language) {
    super(settings);
    myCommenterForm = new CommenterForm(language);
    myCommenterForm.getCommenterPanel().setBorder(IdeBorderFactory.createTitledBorder(XmlCoreBundle.message("comments"), true, new JBInsets(10, 10, 10, 10)));
  }

  @Override
  protected @TabTitle @NotNull String getTabTitle() {
    return ApplicationBundle.message("title.code.generation");
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  protected @Nullable EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
    return null;
  }

  @Override
  protected @NotNull FileType getFileType() {
    return UnknownFileType.INSTANCE;
  }

  @Override
  protected @Nullable String getPreviewText() {
    return null;
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    myCommenterForm.apply(settings);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return myCommenterForm.isModified(settings);
  }

  @Override
  public @Nullable JComponent getPanel() {
    return myCommenterForm.getCommenterPanel();
  }

  @Override
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    myCommenterForm.reset(settings);
  }
}
