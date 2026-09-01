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
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewDocumentVersion
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.editor.livepreview.computeLivePreviewSpecs
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
    val documentVersion = MarkdownLivePreviewDocumentVersion.capture(editor.document, psiFile.project)
    if (editor.livePreviewSpecSetFlow().value?.documentVersion?.matchesDocument(documentVersion) == true) {
      return null
    }
    return MarkdownLivePreviewPass(editor, psiFile)
  }
}

private class MarkdownLivePreviewPass(editor: Editor, psiFile: PsiFile):
  EditorBoundHighlightingPass(editor, psiFile, false), DumbAware {

  private var specSet: MarkdownLivePreviewSpecSet? = null

  override fun doCollectInformation(progress: ProgressIndicator) {
    val elements = computeLivePreviewSpecs(myFile)
    specSet = MarkdownLivePreviewSpecSet(
      MarkdownLivePreviewDocumentVersion.capture(myEditor.document, myFile.project).withElements(elements),
      elements,
    )
  }

  override fun doApplyInformationToEditor() {
    val specSet = specSet ?: return
    if (!specSet.documentVersion.matches(myEditor.document, myFile.project)) return
    myEditor.livePreviewSpecSetFlow().value = specSet
  }
}
