/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class GeneratedCodeFoldingPassFactory implements TextEditorHighlightingPassFactory {
  private TextEditorHighlightingPassRegistrar myRegistrar;

  public GeneratedCodeFoldingPassFactory(final TextEditorHighlightingPassRegistrar registrar) {
    myRegistrar = registrar;
  }

  public TextEditorHighlightingPass createHighlightingPass(@Nullable PsiFile file, final Editor editor) {
    if (file != null && file.getFileType().equals(StdFileTypes.JAVA)) {
      return new GeneratedCodeFoldingPass(file, editor);
    }
    return null;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "GeneratedCodeFoldingPassFactory";
  }

  public void initComponent() {
    myRegistrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false);
  }

  public void disposeComponent() {
  }
}
