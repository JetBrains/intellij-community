// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.preview

import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.fileEditor.createdFileEditorSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditorProvider
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class MarkdownSplitEditorProvider : TextEditorWithPreviewProvider(MarkdownPreviewFileEditorProvider()) {

  /** Waits a short time for live preview, so an editor that opens in live preview does not show its source markup first. */
  override suspend fun createSplitEditorAsync(project: Project, firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    val settings = MarkdownSettings.getInstanceAsync(project)
    val createdEditors = createdFileEditorSink()
    val splitEditor = withContext(Dispatchers.EDT) {
      createMarkdownSplitEditor(firstEditor, secondEditor, settings).also { createdEditors?.register(it) }
    }
    awaitLivePreview(firstEditor.editor)
    return splitEditor
  }

  override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    return createMarkdownSplitEditor(firstEditor, secondEditor, MarkdownSettings.getInstance(getProject(firstEditor)))
  }

  private fun createMarkdownSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor, settings: MarkdownSettings): FileEditor {
    require(secondEditor is MarkdownPreviewFileEditor) { "Secondary editor should be MarkdownPreviewFileEditor" }
    return MarkdownEditorWithPreview(firstEditor, secondEditor, getProject(firstEditor), settings)
  }

  private fun getProject(firstEditor: TextEditor): Project = currentOrDefaultProject(firstEditor.getEditor().getProject())
}

/** After the timeout, the editor shows its source, and live preview applies when its specs arrive. */
private suspend fun awaitLivePreview(editor: Editor) {
  if (!editor.isLivePreviewEnabled()) return
  withTimeoutOrNull(Registry.intValue("markdown.live.preview.opening.timeout.ms").milliseconds) {
    MarkdownLivePreviewReconciler.awaitPresentation(editor)
  }
}
