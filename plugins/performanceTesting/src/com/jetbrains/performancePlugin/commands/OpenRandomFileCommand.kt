package com.jetbrains.performancePlugin.commands

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.swing.Icon

class OpenRandomFileCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openRandomFile"
    private val cache: Cache<String, Set<VirtualFile>> = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()
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
    OpenFileCommand("${OpenFileCommand.PREFIX} ${file.path}", -1).execute(context).onError {
      throw IllegalStateException("fail to open file $file", it)
    }.blockingGet(1, TimeUnit.MINUTES)
  }

  private fun limitedFiles(files: Collection<VirtualFile>,
                           percentOfFiles: Int,
                           maxFilesPerPart: Int,
                           minFileSize: Int): Collection<VirtualFile> {
    val sortedBySize = files.filter { Files.size(it.toNioPath()) > minFileSize }.map {
      it to Files.size(it.toNioPath())
    }.sortedByDescending { it.second }
    val numberOfFiles = minOf((sortedBySize.size * percentOfFiles) / 100, maxFilesPerPart)

    val topFiles = sortedBySize.take(numberOfFiles).map { it.first }
    val midFiles = sortedBySize.take(sortedBySize.size / 2 + numberOfFiles / 2).takeLast(numberOfFiles).map { it.first }
    val lastFiles = sortedBySize.takeLast(numberOfFiles).map { it.first }

    return LinkedHashSet(topFiles + midFiles + lastFiles)
  }

  private fun fileTypeOf(extension: String): FileType {
    return object : FileType {
      override fun getName(): String {
        return when (extension) {
          "kt" -> "Kotlin"
          "java" -> "JAVA"
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

}