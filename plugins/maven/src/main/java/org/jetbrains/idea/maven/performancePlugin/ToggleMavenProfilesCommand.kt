package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsManager

/**
 * The command enables/disables maven profiles by profile ids
 * Syntax: %toggleMavenProfile [profile-id,profile-id-2] [enable]
 * Example: %toggleMavenProfile profile-1,profile-2 true
 */
class ToggleMavenProfilesCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "toggleMavenProfiles"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project

    val (profileIds, enable) = extractCommandList(PREFIX, " ")
    val profileIdsSet = profileIds.split(",").toSet()
    val projectsManager = MavenProjectsManager.getInstance(project)
    val availableProfiles = projectsManager.availableProfiles
    if (!availableProfiles.containsAll(profileIdsSet))
      throw IllegalArgumentException("Available profiles $availableProfiles does not contains all selected profiles $profileIdsSet}")

    val newExplicitProfiles: MavenExplicitProfiles = projectsManager.getExplicitProfiles().clone()
    if (enable.toBoolean()) {
      newExplicitProfiles.disabledProfiles.removeAll(profileIdsSet)
      newExplicitProfiles.enabledProfiles.addAll(profileIdsSet)
    }
    else {
      newExplicitProfiles.enabledProfiles.removeAll(profileIdsSet)
      newExplicitProfiles.disabledProfiles.addAll(profileIdsSet)
    }
    projectsManager.explicitProfiles = newExplicitProfiles
  }

  override fun getName(): String {
    return NAME
  }
}