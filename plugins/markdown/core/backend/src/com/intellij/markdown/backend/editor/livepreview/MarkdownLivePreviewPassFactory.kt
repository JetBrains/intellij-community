// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeHighlighting.Pass
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.editor.livepreview.supportsLivePreview
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
    val shouldNotCreate = when {
      editor.computesLivePreview() -> editor.hasCurrentLivePreviewSpecs(psiFile.project)
      else -> editor.livePreviewSpecSetFlow().value == null
    }
    return if (shouldNotCreate) null else MarkdownLivePreviewPass(editor, psiFile)
  }
}

private class MarkdownLivePreviewPass(editor: Editor, psiFile: PsiFile):
  EditorBoundHighlightingPass(editor, psiFile, false), DumbAware {

  private var specSet: MarkdownLivePreviewSpecSet? = null

  override fun doCollectInformation(progress: ProgressIndicator) {
    specSet = if (myEditor.computesLivePreview()) computeLivePreviewSpecs(myFile, myEditor) else null
  }

  override fun doApplyInformationToEditor() {
    myEditor.publishLivePreviewSpecs(specSet)
  }
}

/**
 * Publishes the specs of the committed document at once, so an editor that turns live preview on does not wait
 * for the next highlighting pass. After that, the pass keeps the specs current.
 */
internal suspend fun Editor.publishCurrentLivePreviewSpecs() {
  val project = project ?: return
  val specSet = readAction {
    val documentManager = PsiDocumentManager.getInstance(project)
    val file = documentManager.getPsiFile(document)?.takeIf { it.language.isMarkdownLanguage() }
    when {
      file == null || !documentManager.isCommitted(document) || hasCurrentLivePreviewSpecs(project) -> null
      else -> computeLivePreviewSpecs(file, this)
    }
  } ?: return
  // Document changes happen on the EDT, so the version check stays true until the publication.
  withContext(Dispatchers.EDT) {
    if (specSet.documentVersion.matches(document, project)) {
      publishLivePreviewSpecs(specSet)
    }
  }
}

/**
 * Publishes [specSet] unless a spec set for the same document is already there.
 * That spec set can hold image sources that loaded after [specSet] was computed.
 */
private fun Editor.publishLivePreviewSpecs(specSet: MarkdownLivePreviewSpecSet?) {
  livePreviewSpecSetFlow().update { current ->
    if (specSet != null && current?.documentVersion?.matchesDocument(specSet.documentVersion) == true) current else specSet
  }
}

private fun Editor.hasCurrentLivePreviewSpecs(project: Project): Boolean {
  return livePreviewSpecSetFlow().value?.documentVersion?.matches(document, project) == true
}

/**
 * Live preview is on when the frontend state is visible on this editor, which is the monolith case,
 * or when the frontend requested it over RPC, which is the split-mode case.
 */
private fun Editor.computesLivePreview(): Boolean = supportsLivePreview() || isLivePreviewRequested()
