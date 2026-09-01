// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.MarkdownImageLoader
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewImage
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.util.MarkdownApplicationScope

/** Resolves and refreshes image sources for one backend editor. */
internal class MarkdownLivePreviewImageManager(
  private val project: Project,
  private val editor: Editor,
) : Disposable {
  private sealed interface LoadRequest {
    data class Cached(val source: VirtualFile) : LoadRequest
    data class Pending(val deferred: Deferred<VirtualFile?>) : LoadRequest
    data object Rejected : LoadRequest
  }

  private val document = FileDocumentManager.getInstance().getFile(editor.document)
    ?: error("Markdown live preview editor has no document file")
  private val loadedSources = LinkedHashMap<String, VirtualFile>()
  private val loadingSources = HashMap<String, Deferred<VirtualFile?>>()
  private val rejectedSources = HashSet<String>()
  private val coroutineScope = MarkdownApplicationScope.createChildScope()
  private val stateLock = Any()

  @Volatile
  private var disposed = false

  init {
    project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        invalidate(events)
      }
    })
  }

  suspend fun load(destination: String): MarkdownLivePreviewImage? {
    val source = loadSource(destination)
    val image = source?.let { MarkdownLivePreviewImage(it.rpcId()) }
    publishSource(destination, image)
    return image
  }

  private suspend fun loadSource(destination: String): VirtualFile? {
    val request = synchronized(stateLock) {
      if (disposed) return@synchronized LoadRequest.Rejected

      val cachedSource = loadedSources[destination]
      if (cachedSource != null && cachedSource.isValid) {
        LoadRequest.Cached(cachedSource)
      }
      else {
        loadedSources.remove(destination)
        rejectedSources.remove(destination)
        LoadRequest.Pending(startLoadingLocked(destination))
      }
    }

    return when (request) {
      is LoadRequest.Cached -> request.source
      is LoadRequest.Pending -> request.deferred.await()
      LoadRequest.Rejected -> null
    }
  }

  private fun startLoadingLocked(destination: String): Deferred<VirtualFile?> {
    loadingSources[destination]?.let { return it }

    val deferred = coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
      loadImage(destination)
    }
    loadingSources[destination] = deferred
    deferred.start()
    return deferred
  }

  private suspend fun loadImage(destination: String): VirtualFile? {
    val source = try {
      withBackgroundProgress(project, MarkdownBundle.message("markdown.image.loading"), cancellable = true) {
        MarkdownImageLoader.load(project, document, destination)
      }
    }
    catch (throwable: Throwable) {
      rethrowControlFlowException(throwable)
      LOG.warn("Failed to load Markdown image $destination", throwable)
      null
    }

    val (completion, result) = synchronized(stateLock) {
      loadingSources.remove(destination)
      if (disposed) {
        false to null
      }
      else {
        if (source == null) {
          loadedSources.remove(destination)
          rejectedSources.add(destination)
        }
        else {
          rejectedSources.remove(destination)
          loadedSources[destination] = source
        }
        true to source
      }
    }
    return if (completion) result else null
  }

  private fun invalidate(events: List<VFileEvent>) {
    if (disposed) return
    val files = events.mapNotNull { it.file }.toSet()
    if (files.isEmpty()) return

    val currentSpecSet = editor.livePreviewSpecSetFlow().value ?: return
    val destinations = currentSpecSet.elements
      .filterIsInstance<MarkdownLivePreviewSpec.Image>()
      .mapTo(LinkedHashSet()) { it.destination }

    val reloads = synchronized(stateLock) {
      if (disposed) return@synchronized emptyList()

      val destinationsToReload = destinations.filterTo(LinkedHashSet()) { destination ->
        destination in rejectedSources || loadedSources[destination] in files
      }
      destinationsToReload.map { destination ->
        rejectedSources.remove(destination)
        loadedSources.remove(destination)
        destination to startLoadingLocked(destination)
      }
    }

    reloads.forEach { (destination, deferred) ->
      coroutineScope.launch(Dispatchers.Default) {
        val source = try {
          deferred.await()
        }
        catch (throwable: Throwable) {
          rethrowControlFlowException(throwable)
          LOG.warn("Failed to refresh Markdown image $destination", throwable)
          null
        }
        publishSource(destination, source?.let { MarkdownLivePreviewImage(it.rpcId()) })
      }
    }
  }

  private fun publishSource(destination: String, image: MarkdownLivePreviewImage?) {
    if (disposed) return
    val specFlow = editor.livePreviewSpecSetFlow()
    val currentSpecSet = specFlow.value ?: return
    var changed = false
    val updatedElements = currentSpecSet.elements.map { spec ->
      if (spec is MarkdownLivePreviewSpec.Image && spec.destination == destination && spec.source != image) {
        changed = true
        spec.copy(source = image)
      }
      else {
        spec
      }
    }
    if (changed) {
      specFlow.value = MarkdownLivePreviewSpecSet(
        currentSpecSet.documentVersion.withElements(updatedElements, sourceHash(updatedElements)),
        updatedElements,
      )
    }
  }

  private fun sourceHash(elements: List<MarkdownLivePreviewSpec>): Int {
    return synchronized(stateLock) {
      elements.filterIsInstance<MarkdownLivePreviewSpec.Image>()
        .map { it.destination to loadedSources[it.destination]?.modificationStamp }
        .hashCode()
    }
  }

  override fun dispose() {
    synchronized(stateLock) {
      if (disposed) return
      disposed = true
      loadedSources.clear()
      loadingSources.clear()
      rejectedSources.clear()
    }
    coroutineScope.cancel()
  }
}

private val LOG = logger<MarkdownLivePreviewImageManager>()

private val IMAGE_MANAGER_KEY = Key.create<MarkdownLivePreviewImageManager>("markdown.live.preview.image.manager")

internal fun Editor.getOrCreateMarkdownLivePreviewImageManager(): MarkdownLivePreviewImageManager {
  val project = checkNotNull(project)
  return (this as UserDataHolderEx).getOrCreateUserData(IMAGE_MANAGER_KEY) {
    MarkdownLivePreviewImageManager(project, this).also { manager ->
      EditorUtil.disposeWithEditor(this, manager)
    }
  }
}
