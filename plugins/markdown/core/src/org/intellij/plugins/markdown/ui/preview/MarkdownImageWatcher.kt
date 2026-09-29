// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

/**
 * Gives a new version to each image of a Markdown document whose file changes.
 * The version is the modification stamp of the file, or [MISSING] if the file is gone.
 *
 * @param onChanged gets the sources of the images with a new version. It runs on the watcher's coroutine, so it must not block.
 * @param resolve finds the file of an image source by the rule of the caller.
 */
@ApiStatus.Internal
class MarkdownImageWatcher<T : Any>(
  coroutineScope: CoroutineScope,
  private val onChanged: (Set<String>) -> Unit = {},
  private val resolve: suspend (String) -> VirtualFile?,
) {
  private val watchedImages = AtomicReference(WatchedImages<T>())

  init {
    val changes = Channel<FileChange>(Channel.UNLIMITED)
    val connection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
    connection.subscribe(VirtualFileManager.VFS_CHANGES_BG, object : BulkFileListenerBackgroundable {
      override fun after(events: List<VFileEvent>) {
        fileChange(events)?.let(changes::trySend)
      }
    })
    coroutineScope.launch {
      for (first in changes) {
        val changed = HashSet(first.changed)
        val suspects = HashSet(first.suspects)
        generateSequence { changes.tryReceive().getOrNull() }.forEach { next ->
          changed += next.changed
          suspects += next.suspects
        }
        try {
          val sources = changed + resolveAgain(suspects)
          if (sources.isNotEmpty()) {
            watchedImages.updateAndGet { it.withNewVersions(sources) }
            onChanged(sources)
          }
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          thisLogger().error("Failed to check the changed image files of the Markdown preview", e)
        }
      }
    }
  }

  fun versionOf(source: String): Long? = watchedImages.get().versionOf(source)

  /** Watches only [sources], the images that the document shows now. */
  fun watch(sources: Set<String>) {
    val snapshot = sources.toSet()
    watchedImages.updateAndGet { it.watch(snapshot) }
  }

  fun loadedValueOf(source: String): T? = watchedImages.get().valueOf(source)

  /** Records the [file] and the [value] of [source]. A new version drops the value. */
  fun onImageLoaded(source: String, file: VirtualFile?, value: T? = null) {
    watchedImages.updateAndGet { it.withFile(source, file, value) }
  }

  private fun fileChange(events: List<VFileEvent>): FileChange? {
    val watched = watchedImages.get()
    if (watched.isEmpty()) {
      return null
    }
    val changed = HashSet<String>()
    val suspects = HashSet<String>()
    for (event in events) {
      when (event) {
        is VFileContentChangeEvent -> watched.named(event.file.name).filterTo(changed) { watched.fileOf(it) == event.file }
        is VFileCreateEvent -> suspects += watched.named(event.childName)
        is VFileCopyEvent -> suspects += watched.named(event.newChildName)
        is VFileDeleteEvent -> suspects += watched.named(event.file.name)
        is VFileMoveEvent -> suspects += watched.named(event.file.name)
        is VFilePropertyChangeEvent -> if (event.isRename) {
          suspects += watched.named(event.oldValue as String)
          suspects += watched.named(event.newValue as String)
        }
      }
    }
    return if (changed.isEmpty() && suspects.isEmpty()) null else FileChange(changed, suspects)
  }

  private suspend fun resolveAgain(sources: Set<String>): Set<String> {
    return sources.filterTo(HashSet()) { source ->
      val file = resolve(source)
      var isDifferent = false
      watchedImages.updateAndGet { watched ->
        isDifferent = watched.isRecorded(source) && watched.fileOf(source) != file
        watched.withFile(source, file)
      }
      isDifferent
    }
  }

  companion object {
    /** The version of an image whose file is gone. */
    const val MISSING: Long = -1
  }
}

private class FileChange(val changed: Set<String>, val suspects: Set<String>)

/**
 * The images that the document shows now, with the file and the value of each. No file means a missing image.
 * The [versions] stay when an image leaves the document, so the image does not go back to an old URL.
 */
private class WatchedImages<T : Any>(
  private val names: ImageNames = ImageNames(emptySet()),
  private val records: Map<String, LoadedRecord<T>> = emptyMap(),
  private val versions: Map<String, Long> = emptyMap(),
) {
  fun isEmpty(): Boolean = records.isEmpty()

  fun named(name: String): Set<String> = names.sourcesNamed(name)

  fun isRecorded(source: String): Boolean = source in records

  fun fileOf(source: String): VirtualFile? = records[source]?.file

  fun valueOf(source: String): T? = records[source]?.value

  fun versionOf(source: String): Long? = versions[source]

  fun watch(sources: Set<String>): WatchedImages<T> {
    if (sources == names.sources) {
      return this
    }
    return WatchedImages(ImageNames(sources), records.filterKeys { it in sources }, versions)
  }

  /** A [value] of null keeps the value of the same file. */
  fun withFile(source: String, file: VirtualFile?, value: T? = null): WatchedImages<T> {
    val old = records[source]
    val newValue = value ?: old?.value?.takeIf { old.file == file }
    if (old != null && old.file == file && old.value == newValue) {
      return this
    }
    return WatchedImages(names, records + (source to LoadedRecord(file, newValue)), versions)
  }

  /** Gives [sources] the stamp of their files as the version, and drops their values. */
  fun withNewVersions(sources: Set<String>): WatchedImages<T> {
    val cleared = records.mapValues { (source, record) -> if (source in sources) LoadedRecord<T>(record.file, null) else record }
    val stamps = sources.associateWith { records[it]?.file?.modificationStamp ?: MarkdownImageWatcher.MISSING }
    return WatchedImages(names, cleared, versions + stamps)
  }
}

private class LoadedRecord<T : Any>(val file: VirtualFile?, val value: T?)

/** The index of [sources] by each name on their paths. */
private class ImageNames(val sources: Set<String>) {
  private val sourcesByName: Map<String, Set<String>> by lazy {
    CollectionFactory.createFilePathMap<MutableSet<String>>().also { index ->
      for (source in sources) {
        for (name in MarkdownPreviewPathResolver.pathNames(source)) {
          index.getOrPut(name) { HashSet() } += source
        }
      }
    }
  }

  fun sourcesNamed(name: String): Set<String> = sourcesByName[name].orEmpty()
}
