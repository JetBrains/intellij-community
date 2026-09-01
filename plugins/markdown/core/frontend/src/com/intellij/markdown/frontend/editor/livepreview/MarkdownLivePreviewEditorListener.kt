// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.EditorScopeProvider
import com.intellij.openapi.editor.impl.editorIdOrNull
import fleet.rpc.client.durable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewRemoteApi
import org.intellij.plugins.markdown.lang.supportsMarkdown

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
          collectSpecs(editor, editorId)
        }
        catch (exception: Throwable) {
          rethrowControlFlowException(exception)
          LOG.warn("Markdown live preview synchronization stopped", exception)
        }
      }
    }
  }

  private suspend fun collectSpecs(editor: Editor, editorId: EditorId) {
    durable {
      MarkdownLivePreviewRemoteApi.getInstance().getLivePreviewSpecs(editorId).toFlow().collect { specSet ->
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
