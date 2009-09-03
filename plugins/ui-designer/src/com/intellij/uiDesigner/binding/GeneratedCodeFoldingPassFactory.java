/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class GeneratedCodeFoldingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {

  public GeneratedCodeFoldingPassFactory(Project project,final TextEditorHighlightingPassRegistrar registrar) {
    super(project);
    registrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_FOLDING}, null, false, -1);
  }

  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    if (file.getFileType().equals(StdFileTypes.JAVA)) {
      return new GeneratedCodeFoldingPass(file, editor);
    }
    return null;
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "GeneratedCodeFoldingPassFactory";
  }
}
