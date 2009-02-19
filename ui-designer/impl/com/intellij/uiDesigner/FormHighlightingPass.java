/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;

/**
 * @author yole
 */
public class FormHighlightingPass implements HighlightingPass {
  private final GuiEditor myEditor;

  public FormHighlightingPass(final GuiEditor editor) {
    myEditor = editor;
  }

  public void collectInformation(ProgressIndicator progress) {
    ErrorAnalyzer.analyzeErrors(myEditor, myEditor.getRootContainer(), progress);
  }

  public void applyInformationToEditor() {
    UIDesignerToolWindowManager.getInstance(myEditor.getProject()).refreshErrors();
    myEditor.refreshIntentionHint();
  }
}
