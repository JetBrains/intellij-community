// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.render.DocRenderItem
import com.intellij.codeInsight.documentation.render.DocRenderItemUpdater
import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.editorIdOrNull
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.rpc.client.durable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewImage
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewRemoteApi
import org.intellij.plugins.markdown.ui.preview.MarkdownImageResourceProvider
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.util.MarkdownApplicationScope

internal class MarkdownLivePreviewImageRenderer(
  project: Project,
  private val editor: Editor,
) : Disposable {
  private val items = HashSet<MarkdownImageRenderItem>()
  private val requestedDestinations = HashSet<String>()
  private var imageGeometry = geometry()

  private val coroutineScope = MarkdownApplicationScope.createChildScope()
  private val document = FileDocumentManager.getInstance().getFile(editor.document) ?: error("Markdown live preview editor has no document file")
  private val editorId = editor.editorIdOrNull()
  private val resourceProvider = MarkdownImageResourceProvider(project, document)

  @Volatile
  private var disposed = false

  init {
    Disposer.register(this, PreviewStaticServer.instance.registerResourceProvider(resourceProvider))

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
    val geometry = geometry()
    if (geometry == imageGeometry) return
    imageGeometry = geometry
    DocRenderItemUpdater.updateRenderers(items, true)
  }

  @RequiresEdt
  fun requestImage(destination: String) {
    val editorId = editorId ?: return
    if (disposed || !requestedDestinations.add(destination)) return
    coroutineScope.launch(Dispatchers.Default) {
      try {
        durable {
          MarkdownLivePreviewRemoteApi.getInstance().requestLivePreviewImage(editorId, destination)
        }
      }
      catch (throwable: Throwable) {
        rethrowControlFlowException(throwable)
        LOG.warn("Failed to request Markdown image $destination", throwable)
      }
    }
  }

  @RequiresEdt
  fun resetRequestedImages() {
    requestedDestinations.clear()
  }

  @RequiresEdt
  fun updateItem(item: MarkdownImageRenderItem, image: MarkdownLivePreviewImage, elementsHash: Int) {
    if (disposed || item !in items) return
    val imageUrl = imageUrl(item.destination, elementsHash)
    if (!item.updateSource(image, elementsHash, imageUrl)) return
    DocRenderItemUpdater.updateRenderers(listOf(item), true)
  }

  @RequiresEdt
  fun createItem(range: TextRange, destination: String, image: MarkdownLivePreviewImage, elementsHash: Int): MarkdownImageRenderItem {
    val item = MarkdownImageRenderItem(editor, range, destination, image, elementsHash, imageUrl(destination, elementsHash)) { items.remove(it) }
    items.add(item)
    return item
  }

  private fun imageUrl(destination: String, elementsHash: Int): String {
    val resourceName = MarkdownImageResourceProvider.resourceName(destination)
    return "${PreviewStaticServer.getStaticUrl(resourceProvider, resourceName)}?refresh=$elementsHash"
  }

  override fun dispose() {
    if (disposed) return
    disposed = true
    coroutineScope.cancel()
    requestedDestinations.clear()
    items.toList().forEach { it.dispose() }
    items.clear()
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
}

internal class MarkdownImageRenderItem(
  override val editor: Editor,
  range: TextRange,
  val destination: String,
  image: MarkdownLivePreviewImage,
  private var elementsHash: Int,
  private var imageUrl: String,
  private val onDispose: (MarkdownImageRenderItem) -> Unit,
) : DocRenderItem {
  var source: VirtualFileId = image.source
    private set
  private var disposed = false

  val renderer = DocRenderer(this, true) { MarkdownLivePreviewPositionKeeper(editor) }

  override var textToRender: String = imageText(imageUrl)
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

  @RequiresEdt
  fun updateSource(image: MarkdownLivePreviewImage, elementsHash: Int, imageUrl: String): Boolean {
    if (source == image.source && this.elementsHash == elementsHash) return false
    source = image.source
    this.elementsHash = elementsHash
    this.imageUrl = imageUrl
    textToRender = imageText(imageUrl)
    return true
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    renderer.dispose()
    highlighter.dispose()
    onDispose(this)
  }

  private fun imageText(url: String): String = HtmlChunk.tag("img").attr("src", url).toString()
}

private val LOG = logger<MarkdownLivePreviewImageRenderer>()

private data class MarkdownImageGeometry(
  val width: Int,
  val pixelScale: Double,
)

private const val DEFAULT_IMAGE_WIDTH = 640
