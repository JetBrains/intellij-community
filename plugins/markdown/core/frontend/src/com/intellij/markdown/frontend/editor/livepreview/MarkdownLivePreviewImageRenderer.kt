// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.render.DocRenderItem
import com.intellij.codeInsight.documentation.render.DocRenderItemUpdater
import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.editor.livepreview.MarkdownImageLoader
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import java.util.LinkedHashMap

internal class MarkdownLivePreviewImageManager(
  private val project: Project,
  private val editor: Editor
) : Disposable {
  private val items = HashSet<MarkdownImageRenderItem>()
  private val loadedSources = LinkedHashMap<ImageKey, VirtualFile>()
  private val loadingSources = HashSet<ImageKey>()
  private val rejectedSources = HashSet<ImageKey>()
  private var imageGeometry = geometry()

  @Volatile
  private var disposed = false

  init {
    val foldingModel = editor.foldingModel
    if (foldingModel is FoldingModelEx) {
      foldingModel.addListener(object : FoldingListener {
        override fun beforeFoldRegionDisposed(region: FoldRegion) {
          val customRegion = region as? CustomFoldRegion ?: return
          val item = customRegion.markdownImageRenderItem() ?: return
          if (item !in items) return
          item.dispose()
        }
      }, this)
    }
  }

  @RequiresEdt
  fun updateGeometry() {
    ThreadingAssertions.assertEventDispatchThread()
    val geometry = geometry()
    if (geometry == imageGeometry) return
    imageGeometry = geometry
    DocRenderItemUpdater.updateRenderers(items, true)
  }

  @RequiresEdt
  fun retainSources(destinations: Set<String>) {
    ThreadingAssertions.assertEventDispatchThread()
    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
    val keys = destinations.mapTo(HashSet()) { ImageKey(file, it) }
    loadedSources.keys.retainAll(keys)
    rejectedSources.retainAll(keys)
  }

  @RequiresEdt
  fun load(spec: MarkdownLivePreviewSpec.Image, onImageLoaded: () -> Unit): VirtualFile? {
    ThreadingAssertions.assertEventDispatchThread()
    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
    val key = ImageKey(file, spec.destination)
    if (key in rejectedSources) return null
    val cachedSource = loadedSources[key]
    if (cachedSource != null) {
      if (cachedSource.isValid) return cachedSource
      loadedSources.remove(key)
    }
    if (key !in loadingSources) {
      startLoading(key, onImageLoaded)
    }
    return null
  }

  private fun startLoading(key: ImageKey, onImageLoaded: () -> Unit) {
    if (!loadingSources.add(key)) return
    val file = key.first
    val destination = key.second
    ApplicationManager.getApplication().executeOnPooledThread {
      val source = MarkdownImageLoader.load(project, file, destination)
      invokeLater(ModalityState.any()) {
        val previousSource = loadedSources[key]
        loadingSources.remove(key)
        if (disposed) return@invokeLater
        if (source == null) {
          if (previousSource == null) {
            rejectedSources.add(key)
            return@invokeLater
          }
          loadedSources.remove(key)
          rejectedSources.add(key)
          onImageLoaded()
          return@invokeLater
        }
        rejectedSources.remove(key)
        loadedSources[key] = source
        if (previousSource != null && previousSource == source) {
          refreshRenderers(source)
        }
        onImageLoaded()
      }
    }
  }

  @RequiresEdt
  fun createItem(range: TextRange, source: VirtualFile): MarkdownImageRenderItem {
    ThreadingAssertions.assertEventDispatchThread()
    val item = MarkdownImageRenderItem(editor, range, source) { items.remove(it) }
    items.add(item)
    return item
  }

  fun invalidate(events: List<VFileEvent>, onImageLoaded: () -> Unit) {
    if (disposed) return
    val files = HashSet<VirtualFile>()
    for (event in events) {
      val file = event.file ?: continue
      files.add(file)
    }
    if (files.isEmpty()) return
    invokeLater(ModalityState.any()) {
      if (disposed) return@invokeLater
      rejectedSources.clear()
      val keysToReload = loadedSources.entries
        .filter { entry -> entry.value in files }
        .map { entry -> entry.key }
      for (key in keysToReload) {
        startLoading(key, onImageLoaded)
      }
    }
  }

  override fun dispose() {
    if (disposed) return
    disposed = true
    for (item in items.toList()) {
      item.dispose()
    }
    items.clear()
    loadedSources.clear()
    loadingSources.clear()
    rejectedSources.clear()
  }

  private fun geometry(): MarkdownImageGeometry {
    val visibleWidth = editor.scrollingModel.visibleArea.width
    val contentWidth = editor.contentComponent.width
    val width = when {
      visibleWidth > 0 -> visibleWidth
      contentWidth > 0 -> contentWidth
      else -> DEFAULT_IMAGE_WIDTH
    }
    val scaleContext = ScaleContext.create(editor.contentComponent)
    return try {
      MarkdownImageGeometry(maxOf(1, width), scaleContext.getScale(DerivedScaleType.PIX_SCALE))
    }
    finally {
      scaleContext.dispose()
    }
  }

  private fun refreshRenderers(source: VirtualFile) {
    val affected = items.filter { item -> item.source == source }
    DocRenderItemUpdater.updateRenderers(affected, true)
  }

  private fun invokeLater(state: ModalityState, runnable: Runnable) = ApplicationManager.getApplication().invokeLater(runnable, state)
}

private typealias ImageKey = Pair<VirtualFile, String>

internal class MarkdownImageRenderItem(
  override val editor: Editor,
  range: TextRange,
  val source: VirtualFile,
  private val onDispose: (MarkdownImageRenderItem) -> Unit,
) : DocRenderItem {
  val renderer = DocRenderer(this, true) { MarkdownLivePreviewPositionKeeper(editor) }
  private var disposed = false

  override val textToRender: String = HtmlChunk.tag("img").attr("src", source.url).toString()
  override val highlighter: RangeHighlighter = editor.markupModel.addRangeHighlighter(
    null, range.startOffset, range.endOffset, 0, HighlighterTargetArea.EXACT_RANGE,
  )
  override var foldRegion: CustomFoldRegion? = null
    internal set

  override fun calcFoldingGutterIconRenderer() = null
  override fun setIconVisible(visible: Boolean) = Unit
  override fun toggle() = Unit
  override fun getInlineDocumentation() = null
  override fun getInlineDocumentationTarget() = null

  fun dispose() {
    if (disposed) return
    disposed = true
    renderer.dispose()
    highlighter.dispose()
    onDispose(this)
  }
}

private data class MarkdownImageGeometry(
  val width: Int,
  val pixelScale: Double,
)

private const val DEFAULT_IMAGE_WIDTH = 640
