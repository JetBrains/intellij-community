// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class XmlTagTreeHighlightingPassFactory implements TextEditorHighlightingPassFactory {
  public XmlTagTreeHighlightingPassFactory(TextEditorHighlightingPassRegistrar registrar) {
    registrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, null, false, -1);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull final PsiFile file, @NotNull final Editor editor) {
    if (editor.isOneLineMode()) return null;

    if (!XmlTagTreeHighlightingUtil.isTagTreeHighlightingActive(file)) return null;
    if (!(editor instanceof EditorEx)) return null;

    return new XmlTagTreeHighlightingPass(file, (EditorEx)editor);
  }
}

