package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiManager
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts
import com.intellij.refactoring.move.MoveHandler
import com.jetbrains.performancePlugin.commands.dto.MoveFilesData
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The command moves files to new directory
 * Argument is serialized [MoveFilesData] as json
 * !!!Different MoveHandlerDelegates are used to move files.
 * If you encounter a problem, then perhaps in your case you are using a delegate that has not been used before
 */
class MoveFilesCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "moveFiles"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  private fun findFile(project: Project, path: String): VirtualFile {
    return OpenFileCommand.findFile(path, project) ?: throw IllegalArgumentException("File not found: $path")
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val psiManager = PsiManager.getInstance(project)
    val moveFileData = deserializeOptionsFromJson(extractCommandArgument(PREFIX), MoveFilesData::class.java)
    val tag = if (moveFileData.spanTag.isNotEmpty()) "_${moveFileData.spanTag}" else ""
    withContext(Dispatchers.EDT) {
      //maybe readaction
      writeIntentReadAction {
        val files = moveFileData.files
          .map { file -> findFile(project, file) }
          .map { file -> psiManager.findFile(file) }
          .toTypedArray()
        val toDirectory = psiManager.findDirectory(findFile(project, moveFileData.toDirectory))
        TelemetryManager.getTracer(Scope("MoveFiles")).spanBuilder("$NAME$tag").use {
          withIgnoredConflicts<Throwable> {
            MoveHandler.doMove(project, files, toDirectory, null, null)
          }
        }
      }
    }
  }

  override fun getName(): String {
    return NAME
  }
}
