// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import org.jetbrains.annotations.NotNull;


public class FormHighlightingPass implements HighlightingPass {
  private final GuiEditor myEditor;

  public FormHighlightingPass(final GuiEditor editor) {
    myEditor = editor;
  }

  @Override
  public void collectInformation(@NotNull ProgressIndicator progress) {
    ErrorAnalyzer.analyzeErrors(myEditor, myEditor.getRootContainer(), progress);
  }

  @Override
  public void applyInformationToEditor() {
    DesignerToolWindowManager.getInstance(myEditor).refreshErrors();
    myEditor.refreshIntentionHint();
  }
}