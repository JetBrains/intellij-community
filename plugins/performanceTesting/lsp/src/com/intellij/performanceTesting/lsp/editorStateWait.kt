// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.lsp

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal val DEFAULT_TIMEOUT: Duration = 60.seconds

/** A change that the listeners miss still ends the wait, this much later at most. */
private val RECHECK_INTERVAL: Duration = 200.milliseconds

internal suspend fun selectedEditor(project: Project): Editor = withContext(Dispatchers.EDT) {
  FileEditorManager.getInstance(project).selectedTextEditor ?: error("No text editor is selected")
}

/**
 * Waits until [condition] holds for the selected editor, and fails when it doesn't within [timeout].
 *
 * The check runs when the text or the highlighters of the document, the highlighters of the editor, its inlays
 * or its fold regions change.
 * So the wait ends when the result is on screen, whichever way it got there: the daemon passes apply some of
 * the server results, and the LSP client applies others on its own.
 * The condition is about the state of the editor, not about an event, so a result that is on screen before
 * the wait starts ends it at once.
 * [stateForFailure] describes what the editor has instead, for the message of the failure.
 */
@Suppress("SplitModeApiUsage") // the commands watch the editor of the IDE process that runs them, and the LSP tests run a monolith
internal suspend fun awaitEditorState(
  project: Project,
  what: String,
  timeout: Duration = DEFAULT_TIMEOUT,
  stateForFailure: (Editor) -> String = { "" },
  condition: (Editor) -> Boolean,
) {
  val changes = Channel<Unit>(Channel.CONFLATED)
  val listeners = Disposer.newDisposable("wait for $what")
  try {
    val editor = withContext(Dispatchers.EDT) {
      val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: error("No text editor is selected")
      val markupListener = object : MarkupModelListener {
        override fun afterAdded(highlighter: RangeHighlighterEx) {
          changes.trySend(Unit)
        }

        override fun afterRemoved(highlighter: RangeHighlighterEx) {
          changes.trySend(Unit)
        }
      }
      (DocumentMarkupModel.forDocument(editor.document, project, true) as MarkupModelEx).addMarkupModelListener(listeners, markupListener)
      (editor.markupModel as MarkupModelEx).addMarkupModelListener(listeners, markupListener)
      editor.document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          changes.trySend(Unit)
        }
      }, listeners)
      editor.inlayModel.addListener(object : InlayModel.Listener {
        override fun onAdded(inlay: Inlay<*>) {
          changes.trySend(Unit)
        }

        override fun onRemoved(inlay: Inlay<*>) {
          changes.trySend(Unit)
        }

        // Code Vision reuses its inlay when the entries of a line change
        override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) {
          changes.trySend(Unit)
        }
      }, listeners)
      (editor.foldingModel as FoldingModelEx).addListener(object : FoldingListener {
        override fun onFoldRegionStateChange(region: FoldRegion) {
          changes.trySend(Unit)
        }

        override fun onFoldProcessingEnd() {
          changes.trySend(Unit)
        }
      }, listeners)
      editor
    }

    val reached = withTimeoutOrNull(timeout) {
      while (!readAction { condition(editor) }) {
        withTimeoutOrNull(RECHECK_INTERVAL) { changes.receive() }
      }
    }
    if (reached == null) {
      val state = readAction { stateForFailure(editor) }
      error("The editor of ${editor.virtualFile?.name} did not get $what in $timeout. $state")
    }
  }
  finally {
    Disposer.dispose(listeners)
  }
}

/**
 * The highlighting of the document, which all editors of a file share, and of this editor alone.
 * The highlighting of the element under the caret is of the second kind.
 *
 * The highlighting comes from an iteration over each markup model, as the editor paints it, not from `getAllHighlighters`.
 * The markup model caches that list, and a read that computes it while another thread adds a highlighter can store
 * a list without that highlighter, which stays until the next change of the markup model.
 */
internal fun Editor.highlightInfos(): Sequence<HighlightInfo> {
  val project = project ?: return emptySequence()
  val infos = ArrayList<HighlightInfo>()
  for (markupModel in listOf(DocumentMarkupModel.forDocument(document, project, false), markupModel)) {
    if (markupModel !is MarkupModelEx) continue
    DaemonCodeAnalyzerEx.processHighlights(markupModel, project, null, 0, document.textLength) { infos.add(it) }
  }
  return infos.asSequence()
}

/** `true` for the caret inside the range and at either of its ends: after typing a name, the caret is at the end of it. */
internal fun Editor.isCaretAt(startOffset: Int, endOffset: Int): Boolean = caretModel.offset in startOffset..endOffset
