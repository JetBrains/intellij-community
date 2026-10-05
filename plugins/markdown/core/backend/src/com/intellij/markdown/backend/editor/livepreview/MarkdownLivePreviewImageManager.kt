// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.LoadedImage
import org.intellij.plugins.markdown.editor.livepreview.MarkdownImageLoader
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.ui.preview.MarkdownImageWatcher
import org.intellij.plugins.markdown.util.MarkdownApplicationScope
import java.util.concurrent.ConcurrentHashMap

/** Resolves and refreshes image sources for one backend editor. */
internal class MarkdownLivePreviewImageManager(
  private val project: Project,
  private val editor: Editor,
) : Disposable {
  private val file = FileDocumentManager.getInstance().getFile(editor.document)
    ?: error("Markdown live preview editor has no document file")
  private val loadingSources = ConcurrentHashMap<String, Deferred<MarkdownLivePreviewSpec.ImageSource?>>()
  private val coroutineScope = MarkdownApplicationScope.createChildScope()

  /** Keeps the size of each loaded image. */
  private val watcher = MarkdownImageWatcher<MarkdownLivePreviewSpec.ImageSource>(
    coroutineScope,
    onChanged = ::reload,
    resolve = { destination -> MarkdownImageLoader.findImageFile(project, file, destination) },
  )

  init {
    coroutineScope.launch {
      editor.livePreviewSpecSetFlow().collect { specSet ->
        watcher.watch(specSet?.imageDestinations().orEmpty())
      }
    }
  }

  suspend fun load(destination: String) {
    val source = watcher.loadedValueOf(destination) ?: startLoading(destination).await()
    publishSource(destination, source)
  }

  /** The [MarkdownLivePreviewSpec.ImageSource] already loaded source of [destination], or null when none is loaded. */
  fun findImageData(destination: String): MarkdownLivePreviewSpec.ImageSource? = watcher.loadedValueOf(destination)

  private fun startLoading(destination: String): Deferred<MarkdownLivePreviewSpec.ImageSource?> {
    return loadingSources.computeIfAbsent(destination) {
      coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { loadImage(destination) }
    }.also { it.start() }
  }

  private suspend fun loadImage(destination: String): MarkdownLivePreviewSpec.ImageSource? {
    try {
      val image = withBackgroundProgress(project, MarkdownBundle.message("markdown.image.loading"), cancellable = true) {
        MarkdownImageLoader.load(project, file, destination)
      }
      val source = image?.toImageSource()
      watcher.onImageLoaded(destination, image?.file, source)
      return source
    }
    finally {
      loadingSources.remove(destination)
    }
  }

  private fun reload(destinations: Set<String>) {
    for (destination in destinations) {
      val deferred = startLoading(destination)
      coroutineScope.launch { publishSource(destination, deferred.await()) }
    }
  }

  private fun publishSource(destination: String, source: MarkdownLivePreviewSpec.ImageSource?) {
    editor.livePreviewSpecSetFlow().update { specSet ->
      if (specSet == null) return@update null
      val elements = specSet.elements.map { spec ->
        if (spec is MarkdownLivePreviewSpec.Image && spec.destination == destination) {
          spec.copy(source = source)
        } else spec
      }
      MarkdownLivePreviewSpecSet(specSet.documentVersion, elements)
    }
  }

  override fun dispose() {
    coroutineScope.cancel()
  }
}

private fun MarkdownLivePreviewSpecSet.imageDestinations(): Set<String> {
  return elements.filterIsInstance<MarkdownLivePreviewSpec.Image>().mapTo(HashSet()) { it.destination }
}

private fun LoadedImage.toImageSource(): MarkdownLivePreviewSpec.ImageSource? {
  val (width, height) = size ?: return null
  return MarkdownLivePreviewSpec.ImageSource(file.modificationStamp, width, height)
}

private val IMAGE_MANAGER_KEY = Key.create<MarkdownLivePreviewImageManager>("markdown.live.preview.image.manager")

internal fun Editor.getOrCreateMarkdownLivePreviewImageManager(): MarkdownLivePreviewImageManager {
  val project = checkNotNull(project)
  return (this as UserDataHolderEx).getOrCreateUserData(IMAGE_MANAGER_KEY) {
    MarkdownLivePreviewImageManager(project, this).also { manager ->
      EditorUtil.disposeWithEditor(this, manager)
    }
  }
}
