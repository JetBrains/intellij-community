// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.ide.trustedProjects.TrustedProjects
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
  private val loadingSources = ConcurrentHashMap<String, Deferred<LoadedImage>>()
  private val coroutineScope = MarkdownApplicationScope.createChildScope()

  /** Keeps the loaded state of each image. */
  private val watcher = MarkdownImageWatcher<LoadedImage>(
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
    if (!TrustedProjects.isProjectTrusted(project)) return
    val image = watcher.loadedValueOf(destination) ?: startLoading(destination).await()
    publish(destination, image)
  }

  /** The image of [destination] that is already loaded, or null when none is loaded. */
  fun findImage(destination: String): LoadedImage? = watcher.loadedValueOf(destination)

  private fun startLoading(destination: String): Deferred<LoadedImage> {
    return loadingSources.computeIfAbsent(destination) {
      coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { loadImage(destination) }
    }.also { it.start() }
  }

  private suspend fun loadImage(destination: String): LoadedImage {
    try {
      val image = withBackgroundProgress(project, MarkdownBundle.message("markdown.image.loading"), cancellable = true) {
        MarkdownImageLoader.load(project, file, destination)
      }
      watcher.onImageLoaded(destination, image.file, image)
      return image
    }
    finally {
      loadingSources.remove(destination)
    }
  }

  private fun reload(destinations: Set<String>) {
    for (destination in destinations) {
      val deferred = startLoading(destination)
      coroutineScope.launch { publish(destination, deferred.await()) }
    }
  }

  private fun publish(destination: String, image: LoadedImage) {
    editor.livePreviewSpecSetFlow().update { specSet ->
      if (specSet == null) return@update null
      val elements = specSet.elements.map { spec ->
        if (spec is MarkdownLivePreviewSpec.Image && spec.destination == destination) {
          spec.copy(source = image.source, failure = image.failure)
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
  return elements
    .asSequence()
    .filterIsInstance<MarkdownLivePreviewSpec.Image>()
    .filterNot { it.isBrowserOwned }
    .mapTo(HashSet()) { it.destination }
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
