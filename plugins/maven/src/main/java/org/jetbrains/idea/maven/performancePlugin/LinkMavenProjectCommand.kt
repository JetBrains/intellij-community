package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider

/**
 * The command links a maven project by project pom.xml path
 * Syntax: %linkMavenProject [project_pom_path]
 * Example: %linkGradleProject /opt/user/gradle-projects/gradle-project-with-sources/pom.xml
 */
class LinkMavenProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "linkMavenProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project

    val filePath = extractCommandArgument(PREFIX)
    val projectPomFile = findFile(extractCommandArgument(PREFIX), project)

    if (projectPomFile == null) {
      throw IllegalArgumentException("File not found: $filePath")
    }

    val openProjectProvider = MavenOpenProjectProvider()
    openProjectProvider.linkToExistingProjectAsync(projectPomFile, project)
  }

  override fun getName(): String {
    return NAME
  }
}