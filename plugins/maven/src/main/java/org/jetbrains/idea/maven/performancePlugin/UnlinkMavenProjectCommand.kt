package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager

/**
 * The command unlinks a maven project by project pom.xml path
 * Syntax: %unlinkMavenProject [project_pom_path]
 * Example: %unlinkGradleProject /opt/user/gradle-projects/gradle-project-with-sources/pom.xml
 */
class UnlinkMavenProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "unlinkMavenProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project

    val filePath = extractCommandArgument(PREFIX)
    val projectPomFile = findFile(extractCommandArgument(PREFIX), project)

    if (projectPomFile == null) {
      throw IllegalArgumentException("File not found: $filePath")
    }
    val projectsManager = MavenProjectsManager.getInstance(project)
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        projectsManager.removeManagedFiles(listOf(projectPomFile), null, null)
      }
    }
  }

  override fun getName(): String {
    return NAME
  }
}