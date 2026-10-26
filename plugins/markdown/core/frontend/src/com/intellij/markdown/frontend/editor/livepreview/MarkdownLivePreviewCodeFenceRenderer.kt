// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.impl.EditorScopeProvider
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.roundToInt

internal class MarkdownLivePreviewCodeFenceRenderer(project: Project, private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  private class Rendering(val language: String, val source: String, val job: Job)

  private val renderings = LinkedHashMap<Inlay<CodeFenceInlayRenderer>, Rendering>()
  private var visibleWidth = editor.scrollingModel.visibleArea.width
  private val coroutineScope = EditorScopeProvider.getInstance(project).getEditorScope(editor)

  init {
    editor.scrollingModel.addVisibleAreaListener({ updateGeometry() }, this)
    // A diagram has the colors of the theme, so a new theme renders each diagram again.
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(LafManagerListener.TOPIC, LafManagerListener {
        for ((inlay, rendering) in renderings.entries.toList()) {
          render(inlay, rendering.language, rendering.source)
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

    val obsolete = ArrayList<Inlay<CodeFenceInlayRenderer>>()
    val retainedLines = HashSet<Int>()
    for ((inlay, rendering) in renderings.entries.toList()) {
      val line = if (inlay.isValid) document.getLineNumber(inlay.offset) else -1
      val fence = wanted[line]
      when {
        fence == null || !retainedLines.add(line) -> obsolete += inlay
        fence.language != rendering.language || fence.source != rendering.source -> render(inlay, fence.language, fence.source)
      }
    }
    val missing = wanted.filterKeys { it !in retainedLines }
    if (obsolete.isEmpty() && missing.isEmpty()) return

    EditorScrollingPositionKeeper.perform(editor, false) {
      obsolete.forEach(Disposer::dispose)
      for ((line, source) in missing) {
        val properties = InlayProperties().showAbove(false).relatesToPrecedingText(true)
        val inlay = editor.inlayModel.addBlockElement(document.getLineEndOffset(line), properties, CodeFenceInlayRenderer(editor)) ?: continue
        Disposer.register(inlay) { renderings.remove(inlay)?.job?.cancel() }
        render(inlay, source.language, source.source)
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

  /** Renders [source] into [inlay], and cancels the previous rendering of the inlay. */
  private fun render(inlay: Inlay<CodeFenceInlayRenderer>, language: String, source: String) {
    val job = coroutineScope.launch(Dispatchers.Default + CoroutineName("Markdown live preview code fence")) {
      val currentJob = checkNotNull(currentCoroutineContext()[Job])
      val painter = CodeFenceRenderingService.render(language, source)
      withContext(Dispatchers.EDT) {
        if (renderings[inlay]?.job !== currentJob) return@withContext
        if (!inlay.isValid) return@withContext
        if (painter == null) {
          renderings.remove(inlay)
          Disposer.dispose(inlay)
          return@withContext
        }
        inlay.renderer.painter = painter
        EditorScrollingPositionKeeper.perform(editor, false) { inlay.update() }
      }
    }
    renderings.put(inlay, Rendering(language, source, job))?.job?.cancel()
  }

  private fun updateGeometry() {
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.isEmpty || visibleArea.width == visibleWidth) return
    visibleWidth = visibleArea.width

    val validInlays = renderings.keys.filter { it.isValid }
    if (validInlays.isEmpty()) return
    EditorScrollingPositionKeeper.perform(editor, false) {
      validInlays.forEach(Inlay<*>::update)
    }
  }

  private fun removeInlays() {
    val detached = renderings.entries.toList()
    renderings.clear()
    detached.forEach { it.value.job.cancel() }
    detached.forEach { Disposer.dispose(it.key) }
  }

  override fun dispose() = removeInlays()
}

private class CodeFenceInlayRenderer(private val editor: Editor) : EditorCustomElementRenderer {
  var painter: CodeFencePainter? = null

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = painter?.let { boxSize(it).width + 2 * inset() } ?: 0

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = painter?.let { boxSize(it).height + 2 * inset() } ?: 0

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val painter = this.painter ?: return
    val size = boxSize(painter)
    val bounds = Rectangle(targetRegion.x + inset(), targetRegion.y + inset(), size.width, size.height)
    val graphics = g.create() as Graphics2D
    try {
      graphics.color = EditorColorsManager.getInstance().schemeForCurrentUITheme.defaultBackground
      graphics.fill(bounds)
      painter.paint(graphics, bounds)
    }
    finally {
      graphics.dispose()
    }
  }

  private fun boxSize(painter: CodeFencePainter): Dimension {
    val width = JBUIScale.scale(painter.width).toDouble()
    val height = JBUIScale.scale(painter.height).toDouble()
    val visibleWidth = editor.scrollingModel.visibleArea.width - 2 * inset()
    val maxHeight = MAX_HEIGHT_IN_LINES * editor.lineHeight
    val widthScale = if (visibleWidth > 0) visibleWidth / width else 1.0
    val heightScale = if (maxHeight > 0) maxHeight / height else 1.0
    val scale = minOf(1.0, widthScale, heightScale)
    return Dimension(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
  }

  private fun inset(): Int = JBUI.scale(2)
}

private const val MAX_HEIGHT_IN_LINES = 20