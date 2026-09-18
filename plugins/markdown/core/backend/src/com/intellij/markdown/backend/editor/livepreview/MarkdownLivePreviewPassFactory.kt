// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeHighlighting.Pass
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.intellij.plugins.markdown.lang.isMarkdownLanguage

/** Recomputes live-preview specs when the Markdown PSI changes. */
internal class MarkdownLivePreviewPassFactory:
  TextEditorHighlightingPassFactoryRegistrar,
  TextEditorHighlightingPassFactory,
  DumbAware {

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false, false)
  }

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (!psiFile.language.isMarkdownLanguage()) return null
    val current = editor.livePreviewSpecSetFlow().value
    val shouldNotCreate = when {
      editor.isLivePreviewEnabled() -> current?.documentVersion?.matches(editor.document, psiFile.project) == true
      else -> current == null
    }
    return if (shouldNotCreate) null else MarkdownLivePreviewPass(editor, psiFile)
  }
}

private class MarkdownLivePreviewPass(editor: Editor, psiFile: PsiFile):
  EditorBoundHighlightingPass(editor, psiFile, false), DumbAware {

  private var specSet: MarkdownLivePreviewSpecSet? = null

  override fun doCollectInformation(progress: ProgressIndicator) {
    specSet = if (myEditor.isLivePreviewEnabled()) computeLivePreviewSpecs(myFile, myEditor) else null
  }

  override fun doApplyInformationToEditor() {
    myEditor.livePreviewSpecSetFlow().value = specSet
  }
}
