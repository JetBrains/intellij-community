package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.search.FilenameIndex.getAllFilesByExt
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.io.File
import kotlin.io.path.div

/**
 *  The command collects all files ending with a certain extension under the project dir
 *  The command expects 2 arguments
 *  The first String argument is extension(s) divided by a comma
 *  The second Boolean argument is whether a file should be located in the source roots
 *  Example: %collectAllFiles ks true
 *  The command will collect all files with extension .ks located ander source root
 *  Example: %collectAllFiles kts,yaml false
 *  The command will collect all files with extensions .yaml and .kts under the project
 */

class CollectAllFilesCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "collectAllFiles"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val parameters = extractCommandArgument(PREFIX).split(" ")
    if (parameters.size != 2) {
      throw IllegalArgumentException("Wrong parameters for command $parameters")
    }
    val extensions = parameters[0].split(",")
    val fromSources = parameters[1].toBooleanStrict()
    val collectedFiles: File = (PathManager.getLogDir() / "collected-files.txt").toFile()
    if (!collectedFiles.exists())
      withContext(Dispatchers.IO) {
        collectedFiles.createNewFile()
      }
    withContext(Dispatchers.EDT) {
      val index = ProjectFileIndex.getInstance(project)
      writeIntentReadAction {
        extensions.forEach { extension ->
          getAllFilesByExt(project, extension, GlobalSearchScope.projectScope(project))
            .forEach { file ->
              val searchCondition = when (fromSources) {
                true -> index.isInSourceContent(file)
                else -> true
              }
              if (searchCondition) {
                collectedFiles.appendText(file.path + "\n")
              }
            }
        }
      }
    }
  }
}