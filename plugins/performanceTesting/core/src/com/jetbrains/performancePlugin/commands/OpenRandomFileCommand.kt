// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.NonNls
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import kotlin.time.Duration.Companion.minutes

class OpenRandomFileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openRandomFile"
    private val cache: Cache<String, Set<VirtualFile>> = Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val allFiles = mutableSetOf<VirtualFile>()
    val fileExtension = extractCommandArgument(PREFIX)
    cache.getIfPresent(fileExtension)?.let { allFiles.addAll(it) }
    if (allFiles.isEmpty()) {
      withContext(Dispatchers.EDT) {
        val index = ProjectFileIndex.getInstance(project)
        val fileProcessor = { file: VirtualFile ->
          if (!index.isInLibrary(file) && (index.isInSourceContent(file) || index.isInTestSourceContent(file))) {
            allFiles.add(file)
          }
          true
        }
        FileTypeIndex.processFiles(fileTypeOf(fileExtension), fileProcessor, GlobalSearchScope.projectScope(project))
      }
      cache.put(fileExtension, allFiles)
    }
    val file = allFiles.random()
    withTimeout(1.minutes) {
      OpenFileCommand("${OpenFileCommand.PREFIX} ${file.path}", -1).execute(context).exceptionally {
        throw IllegalStateException("fail to open file $file", it)
      }
        .asDeferred()
        .join()
    }
  }

}

internal fun fileTypeOf(extension: String): FileType {
  return object : FileType {
    override fun getName(): String {
      return when (extension) {
        "kt" -> "Kotlin"
        "java" -> "JAVA"
        "scala" -> "Scala"
        else -> {
          "default"
        }
      }
    }

    override fun getDescription(): String {
      throw NotImplementedError("no implemented")
    }

    override fun getDefaultExtension(): String {
      return extension
    }

    override fun getIcon(): Icon {
      throw NotImplementedError("no implemented")
    }

    override fun isBinary(): Boolean {
      throw NotImplementedError("no implemented")
    }
  }
}