// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.render.DocRenderItem
import com.intellij.codeInsight.documentation.render.DocRenderItemUpdater
import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.EditorScopeProvider
import com.intellij.openapi.editor.impl.editorIdOrNull
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.rpc.client.durable
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewRemoteApi
import org.intellij.plugins.markdown.ui.preview.MarkdownImageResourceProvider
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer

/** Renders the resolved images of one editor as custom fold regions and asks the backend for the unresolved ones. */
internal class MarkdownLivePreviewImageRenderer(
  project: Project,
  private val editor: EditorEx,
) : Disposable {
  private val items = HashSet<MarkdownImageRenderItem>()
  private val requestedDestinations = HashSet<String>()
  private var geometry = currentGeometry()

  private val coroutineScope = EditorScopeProvider.getInstance(project).getEditorScope(editor)
  private val editorId = editor.editorIdOrNull()
  private val resourceProvider = MarkdownImageResourceProvider(project, FileDocumentManager.getInstance().getFile(editor.document))

  init {
    Disposer.register(this, PreviewStaticServer.instance.registerResourceProvider(resourceProvider))
    editor.foldingModel.addListener(object : FoldingListener {
      override fun beforeFoldRegionDisposed(region: FoldRegion) {
        val item = (region as? CustomFoldRegion)?.markdownImageRenderItem() ?: return
        if (items.remove(item)) item.dispose()
      }
    }, this)
    editor.scrollingModel.addVisibleAreaListener({ updateGeometry() }, this)
  }

  /** Asks the backend to resolve [destination] once per document state; see [resetRequestedImages]. */
  @RequiresEdt
  fun requestImage(destination: String) {
    val editorId = editorId ?: return
    if (!requestedDestinations.add(destination)) return
    coroutineScope.launch {
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

  /** Creates the fold region that paints [destination] over the lines of [range], or null if the folding model refuses it. */
  @RequiresEdt
  fun createRegion(range: TextRange, destination: String, elementsHash: Int): CustomFoldRegion? {
    if (range.isEmpty) return null
    val foldingModel = editor.foldingModel
    // A normal region that shares one boundary with the image lines and extends beyond them blocks a custom region.
    foldingModel.getRegionsOverlappingWith(range.startOffset, range.endOffset)
      .filter { it !is CustomFoldRegion }
      .filter { (it.startOffset == range.startOffset && it.endOffset > range.endOffset) ||
                (it.startOffset < range.startOffset && it.endOffset == range.endOffset) }
      .forEach(foldingModel::removeFoldRegion)
    val item = MarkdownImageRenderItem(editor, range, destination, imageUrl(destination, elementsHash))
    val document = editor.document
    val region = foldingModel.addCustomLinesFolding(
      document.getLineNumber(range.startOffset),
      document.getLineNumber(range.endOffset - 1),
      item.renderer,
    )
    if (region == null) {
      item.dispose()
      return null
    }
    items.add(item)
    item.foldRegion = region
    DocRenderItemUpdater.updateRenderers(listOf(item), false)
    return region
  }

  /** Re-renders the image of [region] when [elementsHash] changed, which is how a refreshed source bypasses the image cache. */
  @RequiresEdt
  fun updateRegion(region: FoldRegion, elementsHash: Int) {
    val item = (region as? CustomFoldRegion)?.markdownImageRenderItem() ?: return
    if (item.updateImageUrl(imageUrl(item.destination, elementsHash))) {
      DocRenderItemUpdater.updateRenderers(listOf(item), true)
    }
  }

  private fun updateGeometry() {
    if (editor.scrollingModel.visibleArea.isEmpty) return
    val current = currentGeometry()
    if (current == geometry) return
    geometry = current
    DocRenderItemUpdater.updateRenderers(items, true)
  }

  /** The visible width and the pixel scale, which are what decide the rendered size of an image. */
  private fun currentGeometry(): Pair<Int, Float> {
    return editor.scrollingModel.visibleArea.width to JBUIScale.pixScale(editor.contentComponent.graphicsConfiguration)
  }

  private fun imageUrl(destination: String, elementsHash: Int): String {
    val resourceName = MarkdownImageResourceProvider.resourceName(destination)
    return "${PreviewStaticServer.getStaticUrl(resourceProvider, resourceName)}?refresh=$elementsHash"
  }

  override fun dispose() {
    items.forEach(MarkdownImageRenderItem::dispose)
  }
}

internal class MarkdownImageRenderItem(
  override val editor: Editor,
  range: TextRange,
  val destination: String,
  imageUrl: String,
) : DocRenderItem {
  val renderer = DocRenderer(this, true) { MarkdownLivePreviewPositionKeeper(editor) }

  override var textToRender: String = imageText(imageUrl)
    private set
  override val highlighter: RangeHighlighter = editor.markupModel.addRangeHighlighter(
    null, range.startOffset, range.endOffset, 0, HighlighterTargetArea.EXACT_RANGE,
  )
  override var foldRegion: CustomFoldRegion? = null

  override fun calcFoldingGutterIconRenderer() = null
  override fun setIconVisible(visible: Boolean) = Unit
  override fun toggle() = Unit
  override fun getInlineDocumentation() = null
  override fun getInlineDocumentationTarget() = null

  /** Returns whether the rendered content changed. */
  fun updateImageUrl(imageUrl: String): Boolean {
    val text = imageText(imageUrl)
    if (textToRender == text) return false
    textToRender = text
    return true
  }

  fun dispose() {
    renderer.dispose()
    highlighter.dispose()
  }
}

private fun imageText(url: String): String = HtmlChunk.tag("img").attr("src", url).toString()

private val LOG = logger<MarkdownLivePreviewImageRenderer>()
