// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class XmlTagTreeHighlightingPassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, null, false, -1);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(final @NotNull PsiFile psiFile, final @NotNull Editor editor) {
    if (editor.isOneLineMode()) return null;

    if (!XmlTagTreeHighlightingUtil.isTagTreeHighlightingActive(psiFile)) return null;
    if (!(editor instanceof EditorEx)) return null;

    return new XmlTagTreeHighlightingPass(psiFile, (EditorEx)editor);
  }
}