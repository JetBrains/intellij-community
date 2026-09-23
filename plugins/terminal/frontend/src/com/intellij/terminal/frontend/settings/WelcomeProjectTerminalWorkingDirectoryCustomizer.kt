package com.intellij.terminal.frontend.settings

import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.plugins.terminal.startup.TerminalWorkingDirectoryCustomizer
import java.nio.file.Path

internal class WelcomeProjectTerminalWorkingDirectoryCustomizer : TerminalWorkingDirectoryCustomizer {
  override suspend fun getDefaultStartWorkingDirectory(project: Project): Path? {
    if (WelcomeUtils.isWelcomeProject(project)) {
      return getUserHomePath(project)
    }
    return null
  }

  /**
   * Returns the home path in the environment of the [project].
   * In the case of RemDev, it would be a path to a home directory of the backend machine.
   */
  private suspend fun getUserHomePath(project: Project): Path {
    val eelApi = project.getEelDescriptor().toEelApi()
    return eelApi.userInfo.home.asNioPath()
  }
}
