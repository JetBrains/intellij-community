// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.EditorScopeProvider
import com.intellij.openapi.editor.impl.editorIdOrNull
import fleet.rpc.client.durable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewRemoteApi
import org.intellij.plugins.markdown.editor.livepreview.livePreviewSupportFlow
import org.intellij.plugins.markdown.lang.supportsMarkdown

/** Follows the live-preview state of each Markdown editor and keeps the backend and the reconciler in line with it. */
internal class MarkdownLivePreviewEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (!editor.supportsMarkdown()) return
    ApplicationManager.getApplication().invokeLater {
      if (editor.isDisposed) return@invokeLater
      val project = editor.project ?: return@invokeLater
      val editorId = editor.editorIdOrNull() ?: return@invokeLater
      EditorScopeProvider.getInstance(project).getEditorScope(editor).launch(Dispatchers.Default) {
        try {
          // Skips the initial off state, so an editor that never turns live preview on makes no RPC calls.
          editor.livePreviewSupportFlow().dropWhile { !it }.collectLatest { enabled ->
            synchronize(editor, editorId, enabled)
          }
        }
        catch (exception: Throwable) {
          rethrowControlFlowException(exception)
          LOG.warn("Markdown live preview synchronization stopped", exception)
        }
      }
    }
  }

  private suspend fun synchronize(editor: Editor, editorId: EditorId, enabled: Boolean) {
    durable {
      val api = MarkdownLivePreviewRemoteApi.getInstance()
      api.setLivePreviewSupport(editorId, enabled)
      val specSets = if (enabled) api.getLivePreviewSpecs(editorId).toFlow() else flowOf(null)
      specSets.collect { specSet ->
        withContext(Dispatchers.EDT) {
          MarkdownLivePreviewReconciler.getOrCreate(editor)?.publishSpecs(specSet)
        }
      }
    }
  }

  private companion object {
    private val LOG = logger<MarkdownLivePreviewEditorListener>()
  }
}
