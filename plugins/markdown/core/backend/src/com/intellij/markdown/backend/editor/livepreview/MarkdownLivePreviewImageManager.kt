// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.ide.vfs.VirtualFileId
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.MarkdownImageLoader
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.util.MarkdownApplicationScope
import java.util.concurrent.ConcurrentHashMap

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
  private val loadedSources = ConcurrentHashMap<String, VirtualFile>()
  private val loadingSources = ConcurrentHashMap<String, Deferred<VirtualFile?>>()
  private val rejectedSources = ConcurrentHashMap.newKeySet<String>()
  private val coroutineScope = MarkdownApplicationScope.createChildScope()

  @Volatile
  private var disposed = false

  init {
    project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        invalidate(events)
      }
    })
    coroutineScope.launch(Dispatchers.Default) {
      editor.livePreviewSpecSetFlow().collect { specSet ->
        retainCachedSources(specSet)
      }
    }
  }

  suspend fun load(destination: String): VirtualFileId? {
    val source = loadSource(destination)
    val sourceId = source?.rpcId()
    publishSource(destination, sourceId)
    return sourceId
  }

  private suspend fun loadSource(destination: String): VirtualFile? {
    if (disposed) return null

    val cachedSource = loadedSources[destination]
    val request = when {
      cachedSource != null && cachedSource.isValid -> LoadRequest.Cached(cachedSource)
      else -> {
        if (cachedSource != null) loadedSources.remove(destination, cachedSource)
        rejectedSources.remove(destination)
        startLoading(destination)?.let(LoadRequest::Pending) ?: LoadRequest.Rejected
      }
    }

    return when (request) {
      is LoadRequest.Cached -> request.source
      is LoadRequest.Pending -> request.deferred.await()
      LoadRequest.Rejected -> null
    }
  }

  private fun startLoading(destination: String): Deferred<VirtualFile?>? {
    val deferred = coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
      loadImage(destination)
    }
    val existing = loadingSources.putIfAbsent(destination, deferred)
    if (existing != null) {
      deferred.cancel()
      return existing
    }
    if (disposed) {
      if (loadingSources.remove(destination, deferred)) deferred.cancel()
      return null
    }
    deferred.start()
    return deferred
  }

  private suspend fun loadImage(destination: String): VirtualFile? {
    try {
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

      if (!disposed) {
        if (source == null) {
          loadedSources.remove(destination)
          rejectedSources.add(destination)
        }
        else {
          rejectedSources.remove(destination)
          loadedSources[destination] = source
        }
      }
      return if (disposed) null else source
    }
    finally {
      loadingSources.remove(destination)
    }
  }

  private fun retainCachedSources(specSet: MarkdownLivePreviewSpecSet?) {
    val destinations = specSet?.elements
      ?.filterIsInstance<MarkdownLivePreviewSpec.Image>()
      ?.mapTo(HashSet()) { it.destination }
      ?: emptySet()
    loadedSources.keys.retainAll(destinations)
    rejectedSources.retainAll(destinations)
  }

  private fun invalidate(events: List<VFileEvent>) {
    if (disposed) return
    val files = events.mapNotNull { it.file }.toSet()
    if (files.isEmpty()) return

    val currentSpecSet = editor.livePreviewSpecSetFlow().value ?: return
    val destinations = currentSpecSet.elements
      .filterIsInstance<MarkdownLivePreviewSpec.Image>()
      .mapTo(LinkedHashSet()) { it.destination }

    val destinationsToReload = destinations.filterTo(LinkedHashSet()) { destination ->
      destination in rejectedSources || loadedSources[destination] in files
    }
    val reloads = destinationsToReload.mapNotNull { destination ->
      rejectedSources.remove(destination)
      loadedSources.remove(destination)
      startLoading(destination)?.let { destination to it }
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
        publishSource(destination, source?.rpcId())
      }
    }
  }

  private fun publishSource(destination: String, sourceId: VirtualFileId?) {
    if (disposed) return
    val specFlow = editor.livePreviewSpecSetFlow()
    specFlow.update { currentSpecSet ->
      if (currentSpecSet == null || disposed) return@update currentSpecSet
      val updatedElements = currentSpecSet.elements.map { spec ->
        if (spec is MarkdownLivePreviewSpec.Image && spec.destination == destination && spec.source != sourceId) {
          spec.copy(source = sourceId)
        }
        else {
          spec
        }
      }
      val documentVersion = currentSpecSet.documentVersion.withElements(updatedElements, sourceHash(updatedElements))
      if (currentSpecSet.elements != updatedElements || currentSpecSet.documentVersion != documentVersion) {
        MarkdownLivePreviewSpecSet(documentVersion, updatedElements)
      }
      else {
        currentSpecSet
      }
    }
  }

  private fun sourceHash(elements: List<MarkdownLivePreviewSpec>): Int {
    return elements.filterIsInstance<MarkdownLivePreviewSpec.Image>()
      .map { it.destination to loadedSources[it.destination]?.modificationStamp }
      .hashCode()
  }

  override fun dispose() {
    if (disposed) return
    disposed = true
    loadedSources.clear()
    loadingSources.values.forEach { it.cancel() }
    loadingSources.clear()
    rejectedSources.clear()
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
