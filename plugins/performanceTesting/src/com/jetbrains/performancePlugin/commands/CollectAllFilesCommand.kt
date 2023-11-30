package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
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
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div

class CollectAllFilesCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "collectAllFiles"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val parameters = extractCommandArgument(PREFIX).split(" ")
    if (parameters.size != 1) {
      throw IllegalArgumentException("Wrong parameters for command $parameters")
    }
    val extension = parameters[0]
    val res: Path = PathManager.getLogDir() / "collected-files.txt"
    if (!Files.exists(res))
      withContext(Dispatchers.IO) {
        Files.createFile(res)
      }
    val bufferedWriter = res.bufferedWriter()
    withContext(Dispatchers.EDT) {
      val index = ProjectFileIndex.getInstance(project)
      val fileProcessor = { file: VirtualFile ->
        if (file.extension != "kts" && !index.isInLibrary(file) && index.isInSourceContent(file)) {
          bufferedWriter.write(file.path)
          bufferedWriter.newLine()
        }
        true
      }
      FileTypeIndex.processFiles(fileTypeOf(extension), fileProcessor, GlobalSearchScope.projectScope(project))
    }
    withContext(Dispatchers.IO) {
      bufferedWriter.flush()
      bufferedWriter.close()
    }
  }
}