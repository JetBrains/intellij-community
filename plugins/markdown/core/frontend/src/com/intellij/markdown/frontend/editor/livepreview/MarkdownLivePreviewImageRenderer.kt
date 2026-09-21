// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.impl.EditorScopeProvider
import com.intellij.openapi.editor.impl.editorIdOrNull
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.rpc.client.durable
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewRemoteApi
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.ui.preview.MarkdownImageResourceProvider
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer

private data class ImageInlay(
  val offset: Int,
  val destination: String,
  val source: MarkdownLivePreviewSpec.ImageSource,
  val ordinal: Int, // orders images from one line
)

private data class InlayKey(val line: Int, val destination: String, val ordinal: Int)

internal class MarkdownLivePreviewImageRenderer(project: Project, private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  private val inlays = LinkedHashSet<Inlay<MarkdownLivePreviewImageInlayRenderer>>()
  private val requestedDestinations = HashSet<String>()
  private var visibleWidth = editor.scrollingModel.visibleArea.width

  private val coroutineScope = EditorScopeProvider.getInstance(project).getEditorScope(editor)
  private val resourceProvider = MarkdownImageResourceProvider(project, FileDocumentManager.getInstance().getFile(editor.document))

  init {
    Disposer.register(this, PreviewStaticServer.instance.registerResourceProvider(resourceProvider))
    editor.scrollingModel.addVisibleAreaListener({ updateGeometry() }, this)
  }

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    return presentation(spec as MarkdownLivePreviewSpec.Image)
  }

  private fun presentation(spec: MarkdownLivePreviewSpec.Image): List<MarkdownLivePreviewFold> {
    return if (spec.source == null) emptyList() else listOf(MarkdownLivePreviewFold(spec.range.toTextRange(), spec.placeholderText))
  }

  /** Updates image resources independently of source concealment. */
  override fun documentChanged() {
    requestedDestinations.clear()
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    reconcileImages(presentation?.elements?.mapNotNull { it.spec as? MarkdownLivePreviewSpec.Image }.orEmpty())
  }

  private fun reconcileImages(images: List<MarkdownLivePreviewSpec.Image>) {
    val document = editor.document
    val desired = ArrayList<ImageInlay>()
    var previousOffset = -1
    var ordinal = 0
    for ((range, destination, _, source) in images) {
      if (source == null) {
        requestImage(destination)
        continue
      }
      val endOffset = range.endOffset.coerceIn(0, document.textLength)
      val offset = document.getLineEndOffset(document.getLineNumber(endOffset))
      ordinal = if (offset == previousOffset) ordinal + 1 else 0
      previousOffset = offset
      desired += ImageInlay(offset, destination, source, ordinal)
    }
    reconcileInlays(desired)
  }

  /** Asks the backend to resolve [destination] once per document state. */
  private fun requestImage(destination: String) {
    val editorId = editor.editorIdOrNull() ?: return
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

  /**
   * Brings the owned inlays in line with [desired]. An inlay survives while its line, image, order, and stamp
   * stay the same. A new stamp replaces the inlay with one that holds a new renderer and a new image URL.
   */
  private fun reconcileInlays(desired: List<ImageInlay>) {
    val document = editor.document
    val obsolete = ArrayList<Inlay<MarkdownLivePreviewImageInlayRenderer>>()
    val owned = HashMap<InlayKey, Inlay<MarkdownLivePreviewImageInlayRenderer>>()
    for (inlay in inlays) {
      if (!inlay.isValid) continue
      val key = InlayKey(document.getLineNumber(inlay.offset), inlay.renderer.destination, -inlay.properties.priority)
      if (owned.putIfAbsent(key, inlay) != null) obsolete += inlay
    }

    val retained = ArrayList<Inlay<MarkdownLivePreviewImageInlayRenderer>>()
    val missing = ArrayList<ImageInlay>()
    for (wanted in desired) {
      val inlay = owned.remove(InlayKey(document.getLineNumber(wanted.offset), wanted.destination, wanted.ordinal))
      if (inlay != null && inlay.renderer.source.stamp == wanted.source.stamp) {
        retained += inlay
        continue
      }
      if (inlay != null) obsolete += inlay
      missing += wanted
    }
    obsolete += owned.values

    inlays.clear()
    inlays += retained
    if (obsolete.isEmpty() && missing.isEmpty()) return

    EditorScrollingPositionKeeper.perform(editor, false) {
      obsolete.forEach(Disposer::dispose)
      for ((offset, destination, source, ordinal) in missing) {
        // A higher priority sits closer to the line, so the first image of a line gets the highest one.
        val properties = InlayProperties().showAbove(false).relatesToPrecedingText(true).priority(-ordinal)
        val renderer = MarkdownLivePreviewImageInlayRenderer(editor, destination, source, imageUrl(destination, source.stamp))
        editor.inlayModel.addBlockElement(offset, properties, renderer)?.let { inlays += it }
      }
    }
  }

  private fun imageUrl(destination: String, stamp: Long): String {
    val resourceName = MarkdownImageResourceProvider.resourceName(destination)
    return "${PreviewStaticServer.getStaticUrl(resourceProvider, resourceName)}?refresh=$stamp"
  }

  private fun updateGeometry() {
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.isEmpty || visibleArea.width == visibleWidth) return
    visibleWidth = visibleArea.width

    val validInlays = inlays.filter { it.isValid }
    if (validInlays.isEmpty()) return
    EditorScrollingPositionKeeper.perform(editor, false) {
      validInlays.forEach(Inlay<*>::update)
    }
  }

  override fun dispose() {
    inlays.filter { it.isValid }.forEach(Disposer::dispose)
    inlays.clear()
  }
}

private val LOG = logger<MarkdownLivePreviewImageRenderer>()
