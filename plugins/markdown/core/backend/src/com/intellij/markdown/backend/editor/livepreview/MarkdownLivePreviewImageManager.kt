// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
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
  private val file = FileDocumentManager.getInstance().getFile(editor.document)
    ?: error("Markdown live preview editor has no document file")
  private val loadedSources = ConcurrentHashMap<String, VirtualFile>()
  private val loadingSources = ConcurrentHashMap<String, Deferred<VirtualFile?>>()
  private val coroutineScope = MarkdownApplicationScope.createChildScope()

  init {
    project.messageBus.connect(coroutineScope).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) = reload(events.mapNotNullTo(HashSet()) { it.file })
    })
    coroutineScope.launch {
      editor.livePreviewSpecSetFlow().collect { specSet ->
        loadedSources.keys.retainAll(specSet?.imageDestinations().orEmpty())
      }
    }
  }

  suspend fun load(destination: String) {
    val source = loadedSources[destination]?.takeIf { it.isValid } ?: startLoading(destination).await()
    publishSource(destination, source)
  }

  private fun startLoading(destination: String): Deferred<VirtualFile?> {
    return loadingSources.computeIfAbsent(destination) {
      coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { loadImage(destination) }
    }.also { it.start() }
  }

  private suspend fun loadImage(destination: String): VirtualFile? {
    try {
      val source = withBackgroundProgress(project, MarkdownBundle.message("markdown.image.loading"), cancellable = true) {
        MarkdownImageLoader.load(project, file, destination)
      }
      if (source == null) loadedSources.remove(destination) else loadedSources[destination] = source
      return source
    }
    finally {
      loadingSources.remove(destination)
    }
  }

  /** Reloads every image that is still unresolved or whose source is one of [changedFiles]. */
  private fun reload(changedFiles: Set<VirtualFile>) {
    if (changedFiles.isEmpty()) return
    val destinations = editor.livePreviewSpecSetFlow().value?.imageDestinations() ?: return
    for (destination in destinations) {
      val source = loadedSources[destination]
      if (source != null && source !in changedFiles) continue
      val deferred = startLoading(destination)
      coroutineScope.launch { publishSource(destination, deferred.await()) }
    }
  }

  private fun publishSource(destination: String, source: VirtualFile?) {
    val sourceId = source?.rpcId()
    editor.livePreviewSpecSetFlow().update { specSet ->
      if (specSet == null) return@update null
      val elements = specSet.elements.map { spec ->
        if (spec is MarkdownLivePreviewSpec.Image && spec.destination == destination) spec.copy(source = sourceId) else spec
      }
      MarkdownLivePreviewSpecSet(specSet.documentVersion.withElements(elements, sourceHash(elements)), elements)
    }
  }

  private fun sourceHash(elements: List<MarkdownLivePreviewSpec>): Int {
    return elements.filterIsInstance<MarkdownLivePreviewSpec.Image>()
      .map { it.destination to loadedSources[it.destination]?.modificationStamp }
      .hashCode()
  }

  override fun dispose() {
    coroutineScope.cancel()
  }
}

private fun MarkdownLivePreviewSpecSet.imageDestinations(): Set<String> {
  return elements.filterIsInstance<MarkdownLivePreviewSpec.Image>().mapTo(HashSet()) { it.destination }
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
