// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.impl.EditorScopeProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.min

internal class MarkdownLivePreviewCodeFenceRenderer(project: Project, private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  private class Rendering(val host: CodeFenceHost, val language: String, val source: String, val job: Job)

  private val renderings = LinkedHashMap<Inlay<*>, Rendering>()
  private val coroutineScope = EditorScopeProvider.getInstance(project).getEditorScope(editor)

  init {
    // A diagram has the colors of the theme, so a new theme renders each diagram again.
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(LafManagerListener.TOPIC, LafManagerListener {
        for ((inlay, rendering) in renderings.entries.toList()) {
          render(inlay, rendering.host, rendering.language, rendering.source)
        }
      })
    // An inlay must not keep the diagram of an unloaded service. The next reconcile adds the inlays again.
    CodeFenceRenderingService.EP_NAME.addChangeListener(coroutineScope) {
      coroutineScope.launch(Dispatchers.EDT) { removeInlays() }
    }
  }

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> = emptyList()

  override fun documentChanged() = Unit

  @RequiresEdt
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    val document = editor.document
    val wanted = codeFences(presentation)

    val obsolete = ArrayList<Inlay<*>>()
    val retainedLines = HashSet<Int>()
    for ((inlay, rendering) in renderings.entries.toList()) {
      val line = if (inlay.isValid) document.getLineNumber(inlay.offset) else -1
      val fence = wanted[line]
      when {
        fence == null || !retainedLines.add(line) -> obsolete += inlay
        fence.language != rendering.language || fence.source != rendering.source -> render(inlay, rendering.host, fence.language, fence.source)
      }
    }
    val missing = wanted.filterKeys { it !in retainedLines }
    if (obsolete.isEmpty() && missing.isEmpty()) return

    EditorScrollingPositionKeeper.perform(editor, false) {
      obsolete.forEach(Disposer::dispose)
      val componentManager = EditorEmbeddedComponentManager.getInstance()
      for ((line, source) in missing) {
        val host = CodeFenceHost(editor)
        val properties = EditorEmbeddedComponentManager.Properties(
          EditorEmbeddedComponentManager.ResizePolicy.none(),
          null,
          true,
          false,
          false,
          false,
          0,
          document.getLineEndOffset(line),
        )
        val inlay = componentManager.addComponent(editor, host, properties) ?: continue
        Disposer.register(inlay) {
          renderings.remove(inlay)?.job?.cancel()
          host.viewJob?.cancel()
        }
        render(inlay, host, source.language, source.source)
      }
    }
  }

  private data class Fence(val language: String, val source: String)

  private fun codeFences(presentation: MarkdownLivePreviewPresentation?): Map<Int, Fence> {
    val document = editor.document
    return presentation?.elements.orEmpty()
      .mapNotNull { element -> (element.spec as? MarkdownLivePreviewSpec.CodeFence) }
      .associate { document.getLineNumber(it.range.endOffset.coerceIn(0, document.textLength)) to Fence(it.language, it.source) }
  }

  /** Renders [source] into [host], and cancels the previous rendering of the inlay. */
  private fun render(inlay: Inlay<*>, host: CodeFenceHost, language: String, source: String) {
    val job = coroutineScope.launch(Dispatchers.Default + CoroutineName("Markdown live preview code fence")) {
      val currentJob = checkNotNull(currentCoroutineContext()[Job])
      val view = CodeFenceRenderingService.render(language, source, this)
      withContext(Dispatchers.EDT) {
        if (renderings[inlay]?.job !== currentJob || !inlay.isValid) return@withContext
        if (view == null) {
          Disposer.dispose(inlay)
          return@withContext
        }
        host.show(view, currentJob)
        EditorScrollingPositionKeeper.perform(editor, false) { inlay.update() }
      }
      awaitCancellation()
    }
    val previousJob = renderings.put(inlay, Rendering(host, language, source, job))?.job
    if (previousJob !== host.viewJob) previousJob?.cancel()
  }

  private fun removeInlays() {
    val detached = renderings.entries.toList()
    renderings.clear()
    detached.forEach { it.value.job.cancel() }
    detached.forEach { Disposer.dispose(it.key) }
  }

  override fun dispose() = removeInlays()
}

private class CodeFenceHost(private val editor: EditorEx) : JPanel(BorderLayout()) {
  private var view: CodeFenceView? = null

  var viewJob: Job? = null
    private set

  init {
    isOpaque = false
    border = JBUI.Borders.empty(2)
  }

  /**
   * Shows [view] in place of the shown view. [job] owns [view].
   * After the replacement, the method cancels the job of the replaced view, so the replaced view closes.
   */
  fun show(view: CodeFenceView, job: Job) {
    val replacedJob = viewJob
    viewJob = job
    this.view = view
    removeAll()
    view.component.background = JBColor.lazy { editor.backgroundColor }
    add(view.component, BorderLayout.CENTER)
    revalidate()
    repaint()
    replacedJob?.cancel()
  }

  override fun getPreferredSize(): Dimension {
    val view = view ?: return Dimension()
    val insets = insets
    val scrollPane = editor.scrollPane
    // if scrollbar is on the left side, we'll need all possible space to fit the component
    val rightWidth = if (editor.verticalScrollbarOrientation == EditorEx.VERTICAL_SCROLLBAR_LEFT) 0 else scrollPane.verticalScrollBar.width
    val left = SwingUtilities.convertPoint(this, 0, 0, editor.contentComponent).x
    val width = (scrollPane.viewport.width - rightWidth - left - insets.left - insets.right).coerceAtLeast(0)
    val natural = view.component.preferredSize
    val fittedHeight = if (natural.width > width) natural.height * width / natural.width else natural.height
    val height = min(fittedHeight, MAX_HEIGHT_IN_LINES * editor.lineHeight)
    return Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom)
  }
}

private const val MAX_HEIGHT_IN_LINES = 20