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
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewReconcilerBridge
import org.intellij.plugins.markdown.editor.livepreview.computeLivePreviewSpecs
import org.intellij.plugins.markdown.lang.isMarkdownLanguage

private val LIVE_PREVIEW_DOCUMENT_STAMP: Key<Long> = Key.create("markdown.live.preview.document.stamp")

/**
 * Recomputes what live preview should hide whenever the Markdown PSI changes.
 *
 * The pass never touches fold regions itself: it hands the specs to [org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewReconcilerBridge], which owns every change to the editor.
 */
internal class MarkdownLivePreviewPassFactory:
  TextEditorHighlightingPassFactoryRegistrar,
  TextEditorHighlightingPassFactory,
  DumbAware {

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_FOLDING, false, false)
  }

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (!psiFile.language.isMarkdownLanguage()) return null
    // The specs are a pure function of this file's own text, so its modification stamp is the whole of what
    // can invalidate them. Tracking PSI project-wide would recompute them after every unrelated edit
    // anywhere in the project, and would put the applied marker on a different clock from the spec set.
    val applied = editor.getUserData(LIVE_PREVIEW_DOCUMENT_STAMP)
    // Still run when no reconciler is attached yet, so a freshly opened editor gets its first specs even if
    // the document has not been touched since.
    if (applied == editor.document.modificationStamp && MarkdownLivePreviewReconcilerBridge.getInstance()?.hasExistingReconciler(editor) == true) {
      return null
    }
    return MarkdownLivePreviewPass(editor, psiFile)
  }
}

private class MarkdownLivePreviewPass(editor: Editor, psiFile: PsiFile):
  EditorBoundHighlightingPass(editor, psiFile, false), DumbAware {

  private var specSet: MarkdownLivePreviewSpecSet? = null

  override fun doCollectInformation(progress: ProgressIndicator) {
    specSet = MarkdownLivePreviewSpecSet(myEditor.document.modificationStamp, computeLivePreviewSpecs(myFile))
  }

  override fun doApplyInformationToEditor() {
    val specSet = specSet ?: return
    val published = MarkdownLivePreviewReconcilerBridge.getInstance()?.publish(myEditor, specSet)
    if (published == true) myEditor.putUserData(LIVE_PREVIEW_DOCUMENT_STAMP, specSet.documentStamp)
  }
}
