// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.tables.ui.alignment

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.tables.ui.alignment.MarkdownTableAlignmentModel
import org.intellij.plugins.markdown.editor.tables.ui.alignment.MarkdownTableAlignmentSettingsListener
import org.intellij.plugins.markdown.editor.tables.ui.alignment.TablePadRenderer
import org.intellij.plugins.markdown.lang.supportsMarkdown
import org.intellij.plugins.markdown.util.MarkdownApplicationScope
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

/** Coalesces editor changes and refreshes table alignment on the EDT. */
@ApiStatus.Internal
class MarkdownTableAlignmentController(private val editor: Editor) : Disposable {
  private val model = MarkdownTableAlignmentModel(editor)
  private val coroutineScope = MarkdownApplicationScope.createChildScope()
  private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

  private var disposed = false

  @RequiresEdt
  fun start() {
    subscribe()
    startRefreshLoop()
    scheduleRefresh()
  }

  override fun dispose() {
    disposed = true
    coroutineScope.cancel()
    Disposer.dispose(model)
  }

  @RequiresEdt
  private fun scheduleRefresh() {
    if (disposed) return
    refreshRequests.trySend(Unit)
  }

  @OptIn(FlowPreview::class)
  private fun startRefreshLoop() {
    coroutineScope.launch {
      refreshRequests.receiveAsFlow()
        .debounce(REFRESH_DELAY)
        .collectLatest {
          withContext(Dispatchers.EDT) {
            performRefresh()
          }
        }
    }
  }

  @RequiresEdt
  private fun performRefresh() {
    if (disposed || editor.isDisposed) return
    val project = editor.project ?: return
    PsiDocumentManager.getInstance(project).performForCommittedDocument(editor.document) {
      if (disposed || editor.isDisposed) {
        return@performForCommittedDocument
      }
      model.refreshVisible()
    }
  }

  private fun subscribe() {
    editor.document.addDocumentListener(object : BulkAwareDocumentListener {
      override fun documentChangedNonBulk(event: DocumentEvent) {
        model.updateEditedCell(event)
        scheduleRefresh()
      }

      override fun bulkUpdateFinished(document: Document) {
        scheduleRefresh()
      }
    }, this)

    (editor.foldingModel as? FoldingModelEx)?.addListener(object : FoldingListener {
      override fun onFoldProcessingEnd() {
        scheduleRefresh()
      }
    }, this)

    editor.inlayModel.addListener(object : InlayModel.Listener {
      override fun onAdded(inlay: Inlay<*>) = onForeignInlayChanged(inlay)
      override fun onRemoved(inlay: Inlay<*>) = onForeignInlayChanged(inlay)
      override fun onUpdated(inlay: Inlay<*>) = onForeignInlayChanged(inlay)
    }, this)

    editor.scrollingModel.addVisibleAreaListener(VisibleAreaListener { event ->
      if (event.oldRectangle != event.newRectangle) {
        scheduleRefresh()
      }
    }, this)

    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      scheduleRefresh()
    })
    connection.subscribe(MarkdownTableAlignmentSettingsListener.TOPIC, MarkdownTableAlignmentSettingsListener {
      recalculateSoftWraps()
      scheduleRefresh()
    })
  }

  private fun recalculateSoftWraps() {
    val softWrapModel = editor.softWrapModel as? SoftWrapModelImpl ?: return
    softWrapModel.applianceManager.recalculateAll("Markdown table alignment setting changed")
    editor.contentComponent.revalidate()
    editor.contentComponent.repaint()
  }

  private fun onForeignInlayChanged(inlay: Inlay<*>) {
    if (inlay.renderer is TablePadRenderer || inlay.placement != Inlay.Placement.INLINE) {
      return
    }
    scheduleRefresh()
  }

  internal class Listener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
      val editor = event.editor
      if (!editor.supportsMarkdown()) {
        return
      }
      val controller = MarkdownTableAlignmentController(editor)
      EditorUtil.disposeWithEditor(editor, controller)
      controller.start()
    }
  }

  private companion object {
    val REFRESH_DELAY = 150.milliseconds
  }
}
